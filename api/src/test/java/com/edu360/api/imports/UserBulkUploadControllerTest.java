package com.edu360.api.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.files.FileObject;
import com.edu360.api.files.FileObjectId;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.files.LocalFileStorage;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.imports.UserBulkImportRowService;
import com.edu360.api.imports.dto.ImportJobResponse;
import com.edu360.api.imports.dto.ImportRowErrorResponse;
import com.edu360.api.imports.dto.ImportTemplateResponse;
import com.edu360.api.imports.dto.StartImportRequest;
import com.edu360.api.imports.dto.UserImportRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.dto.CreateUserRequest;

/**
 * Action & Guard tests for Story #10 (USER_BULK_UPLOAD):
 * - ACT-USER_BULK_UPLOAD-ALLOW
 * - ACT-USER_BULK_UPLOAD-DENY
 * - ACT-USER_BULK_UPLOAD-SCOPE
 * - ACT-USER_BULK_UPLOAD-GUARD (Rule MD-27: CLEAN_ONLY rejected when errors exist)
 * - ACT-USER_BULK_UPLOAD-GUARD (Statutory UIDAI [R2-34]: unmasked 12-digit Aadhaar rejected)
 * - ACT-USER_BULK_UPLOAD-GUARD (Duplicate email in file / database)
 */
class UserBulkUploadControllerTest {

    private ImportJobRepository importJobRepository;
    private ImportRowErrorRepository importRowErrorRepository;
    private FileObjectRepository fileObjectRepository;
    private LocalFileStorage localFileStorage;
    private PermissionEvaluatorService permissionEvaluatorService;
    private UserAccountRepository userAccountRepository;
    private UserBulkImportRowService userAccountService;
    private AuditLogService auditLogService;

    private UserBulkUploadService userBulkUploadService;
    private UserBulkUploadController controller;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID foreignTenantId = UUID.randomUUID();

    private Authentication auth;

    @BeforeEach
    void setUp() {
        importJobRepository = mock(ImportJobRepository.class);
        importRowErrorRepository = mock(ImportRowErrorRepository.class);
        fileObjectRepository = mock(FileObjectRepository.class);
        localFileStorage = mock(LocalFileStorage.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        userAccountRepository = mock(UserAccountRepository.class);
        userAccountService = mock(UserBulkImportRowService.class);
        auditLogService = mock(AuditLogService.class);

        userBulkUploadService = new UserBulkUploadService(
                importJobRepository,
                importRowErrorRepository,
                fileObjectRepository,
                localFileStorage,
                permissionEvaluatorService,
                userAccountRepository,
                userAccountService,
                auditLogService
        );

        controller = new UserBulkUploadController(userBulkUploadService);

        TenantPrincipal principal = new TenantPrincipal(tenantId, userId, com.edu360.api.identity.UserType.STAFF, "Admin", false);
        auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        when(localFileStorage.store(eq(tenantId), any(), any())).thenReturn("tenants/" + tenantId + "/file-key");
    }

    @Test
    @DisplayName("ALLOW: University Admin uploads clean CSV and starts import (Rule MD-27)")
    void testUploadAndStartCleanImport_Success() {
        String csv = "email,fullName,userType,mobile,nationalIdLast4,roleCode\n" +
                "rajesh.kumar@northbridge.edu.in,Rajesh Kumar,STAFF,+919876543210,1234,OFFICE_STAFF\n" +
                "priya.sharma@northbridge.edu.in,Priya Sharma,FACULTY,+919812345678,5678,TEACHER\n";

        when(userAccountRepository.existsByIdTenantIdAndEmail(any(), any())).thenReturn(false);

        // 1. Upload & pre-validate
        ResponseEntity<ImportJobResponse> uploadRes = controller.createUserImport(
                tenantId,
                new UserImportRequest(csv, null, "USERS"),
                auth
        );

        assertThat(uploadRes.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        ImportJobResponse jobRes = uploadRes.getBody();
        assertThat(jobRes).isNotNull();
        assertThat(jobRes.status()).isEqualTo("VALIDATED");
        assertThat(jobRes.totalRows()).isEqualTo(2);
        assertThat(jobRes.validRows()).isEqualTo(2);
        assertThat(jobRes.errorRows()).isEqualTo(0);

        UUID importJobId = jobRes.importJobId();

        // Setup mock job for startImport
        ImportJob mockJob = ImportJob.builder()
                .id(new ImportJobId(tenantId, importJobId))
                .importKind("USERS")
                .uploadedBy(userId)
                .sourceFileId(UUID.randomUUID())
                .status("VALIDATED")
                .totalRows(2)
                .validRows(2)
                .errorRows(0)
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        when(importJobRepository.findByIdTenantIdAndIdImportJobId(tenantId, importJobId)).thenReturn(Optional.of(mockJob));
        when(fileObjectRepository.findById(any())).thenReturn(Optional.of(
                FileObject.builder().storageKey("stored-key").build()
        ));
        when(localFileStorage.read("stored-key")).thenReturn(csv.getBytes(StandardCharsets.UTF_8));

        // 2. Start import with CLEAN_ONLY
        ResponseEntity<ImportJobResponse> startRes = controller.startImport(
                tenantId,
                importJobId,
                new StartImportRequest("CLEAN_ONLY"),
                auth
        );

        assertThat(startRes.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(startRes.getBody().status()).isEqualTo("COMPLETED");

        verify(userAccountService, times(2)).createUser(any(), any());
        verify(auditLogService).record(eq(AuditEventType.USER_BULK_UPLOADED), any(), eq(userId.toString()), any(), any());
    }

    @Test
    @DisplayName("DENY: Caller without USER_BULK_UPLOAD permission is rejected with 403")
    void testUpload_Unauthorized_Denied() {
        doThrow(new AccessDeniedException("Forbidden: missing USER_BULK_UPLOAD"))
                .when(permissionEvaluatorService).authorize(
                        any(),
                        eq("USER_BULK_UPLOAD"),
                        any(),
                        eq(RbacTarget.NONE)
                );

        String csv = "email,fullName,userType\nstaff@test.com,Staff User,STAFF\n";

        assertThatThrownBy(() -> controller.createUserImport(
                tenantId,
                new UserImportRequest(csv, null, "USERS"),
                auth
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("USER_BULK_UPLOAD");

        verify(importJobRepository, never()).save(any());
    }

    @Test
    @DisplayName("SCOPE: Accessing import with mismatching tenant ID is rejected")
    void testScope_ForeignTenant_Rejected() {
        String csv = "email,fullName,userType\nstaff@test.com,Staff User,STAFF\n";

        assertThatThrownBy(() -> controller.createUserImport(
                foreignTenantId,
                new UserImportRequest(csv, null, "USERS"),
                auth
        )).isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("tenantId in the path does not match");
    }

    @Test
    @DisplayName("GUARD (Rule MD-27): CLEAN_ONLY rejected with 422 when validation errors exist")
    void testGuard_CleanOnlyWithErrors_Rejected() {
        UUID importJobId = UUID.randomUUID();
        ImportJob mockJob = ImportJob.builder()
                .id(new ImportJobId(tenantId, importJobId))
                .importKind("USERS")
                .uploadedBy(userId)
                .sourceFileId(UUID.randomUUID())
                .status("VALIDATED")
                .totalRows(2)
                .validRows(1)
                .errorRows(1) // 1 error row
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        when(importJobRepository.findByIdTenantIdAndIdImportJobId(tenantId, importJobId)).thenReturn(Optional.of(mockJob));

        assertThatThrownBy(() -> controller.startImport(
                tenantId,
                importJobId,
                new StartImportRequest("CLEAN_ONLY"),
                auth
        )).isInstanceOf(BusinessRuleViolationException.class)
                .matches(e -> "IMPORT_HAS_ERRORS".equals(((BusinessRuleViolationException) e).getErrorCode()))
                .hasMessageContaining("Cannot start import in CLEAN_ONLY mode");

        verify(userAccountService, never()).createUser(any(), any());
    }

    @Test
    @DisplayName("ALLOW: GOOD_ROWS_ONLY imports clean rows while preserving error rows (Rule MD-27)")
    void testStartImport_GoodRowsOnly_Success() {
        UUID importJobId = UUID.randomUUID();
        String csv = "email,fullName,userType,nationalIdLast4\n" +
                "clean.user@northbridge.edu.in,Clean User,STAFF,1234\n" +
                "bad.user@northbridge.edu.in,Bad User,STAFF,123456789012\n"; // Bad row 3

        ImportJob mockJob = ImportJob.builder()
                .id(new ImportJobId(tenantId, importJobId))
                .importKind("USERS")
                .uploadedBy(userId)
                .sourceFileId(UUID.randomUUID())
                .status("VALIDATED")
                .totalRows(2)
                .validRows(1)
                .errorRows(1)
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        when(importJobRepository.findByIdTenantIdAndIdImportJobId(tenantId, importJobId)).thenReturn(Optional.of(mockJob));
        when(fileObjectRepository.findById(any())).thenReturn(Optional.of(
                FileObject.builder().storageKey("stored-key").build()
        ));
        when(localFileStorage.read("stored-key")).thenReturn(csv.getBytes(StandardCharsets.UTF_8));

        // Row 3 has an error recorded
        ImportRowError row3Error = ImportRowError.builder()
                .id(new ImportRowErrorId(tenantId, UUID.randomUUID()))
                .importJobId(importJobId)
                .rowNumber(3)
                .fieldName("nationalIdLast4")
                .errorCode("UIDAI_MASKED_LAST4_REQUIRED")
                .errorMessage("Unmasked 12-digit Aadhaar")
                .rowData("{}")
                .build();
        when(importRowErrorRepository.findByIdTenantIdAndImportJobIdOrderByRowNumberAsc(tenantId, importJobId))
                .thenReturn(List.of(row3Error));

        ResponseEntity<ImportJobResponse> startRes = controller.startImport(
                tenantId,
                importJobId,
                new StartImportRequest("GOOD_ROWS_ONLY"),
                auth
        );

        assertThat(startRes.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(startRes.getBody().status()).isEqualTo("COMPLETED");

        // Exactly one clean user is created
        ArgumentCaptor<CreateUserRequest> reqCaptor = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userAccountService, times(1)).createUser(any(), reqCaptor.capture());
        assertThat(reqCaptor.getValue().email()).isEqualTo("clean.user@northbridge.edu.in");
    }

    @Test
    @DisplayName("GUARD (UIDAI [R2-34] & DPDP Act 2023): Unmasked 12-digit Aadhaar flags statutory error and masks ID")
    void testGuard_UnmaskedAadhaar_FlagsStatutoryError() {
        String csv = "email,fullName,userType,nationalIdLast4\n" +
                "unmasked.staff@northbridge.edu.in,Unmasked Staff,STAFF,123456789012\n";

        ResponseEntity<ImportJobResponse> uploadRes = controller.createUserImport(
                tenantId,
                new UserImportRequest(csv, null, "USERS"),
                auth
        );

        assertThat(uploadRes.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        ImportJobResponse jobRes = uploadRes.getBody();
        assertThat(jobRes.validRows()).isEqualTo(0);
        assertThat(jobRes.errorRows()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImportRowError>> errorCaptor = ArgumentCaptor.forClass(List.class);
        verify(importRowErrorRepository).saveAll(errorCaptor.capture());

        List<ImportRowError> captured = errorCaptor.getValue();
        assertThat(captured).hasSize(1);
        ImportRowError err = captured.get(0);
        assertThat(err.getErrorCode()).isEqualTo("UIDAI_MASKED_LAST4_REQUIRED");
        assertThat(err.getErrorMessage()).contains("UIDAI and DPDP Act 2023 [R2-34]");
        // Verify plaintext 12-digit Aadhaar is masked in rowData
        assertThat(err.getRowData()).contains("XXXX-XXXX-9012");
        assertThat(err.getRowData()).doesNotContain("123456789012");
    }

    @Test
    @DisplayName("GUARD: Duplicate email in CSV file is detected as row error")
    void testGuard_DuplicateEmailInFile_Flagged() {
        String csv = "email,fullName,userType\n" +
                "duplicate@test.com,First User,STAFF\n" +
                "duplicate@test.com,Second User,STAFF\n";

        ResponseEntity<ImportJobResponse> uploadRes = controller.createUserImport(
                tenantId,
                new UserImportRequest(csv, null, "USERS"),
                auth
        );

        ImportJobResponse jobRes = uploadRes.getBody();
        assertThat(jobRes.validRows()).isEqualTo(1);
        assertThat(jobRes.errorRows()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImportRowError>> errorCaptor = ArgumentCaptor.forClass(List.class);
        verify(importRowErrorRepository).saveAll(errorCaptor.capture());

        assertThat(errorCaptor.getValue().get(0).getErrorCode()).isEqualTo("DUPLICATE_RESOURCE");
        assertThat(errorCaptor.getValue().get(0).getErrorMessage()).contains("Duplicate email found in CSV file");
    }

    @Test
    @DisplayName("Template: getImportTemplate returns CSV headers and sample data (Rule MD-26)")
    void testGetImportTemplate_Success() {
        ResponseEntity<ImportTemplateResponse> res = controller.getImportTemplate(tenantId, "USERS");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        ImportTemplateResponse template = res.getBody();
        assertThat(template).isNotNull();
        assertThat(template.headers()).contains("email", "fullName", "userType", "mobile", "nationalIdLast4");
        assertThat(template.csvTemplate()).contains("email,fullName,userType,mobile,nationalIdLast4");
    }

    @Test
    @DisplayName("H10: Non-creator without USER_BULK_UPLOAD permission is denied reading import job and errors")
    void testGetImportJob_NonCreatorWithoutPermission_Denied() {
        UUID otherUser = UUID.randomUUID();
        UUID importJobId = UUID.randomUUID();
        ImportJob job = ImportJob.builder()
                .id(new ImportJobId(tenantId, importJobId))
                .uploadedBy(otherUser)
                .status("VALIDATED")
                .totalRows(1)
                .validRows(1)
                .errorRows(0)
                .createdAt(Instant.now())
                .createdBy(otherUser)
                .updatedAt(Instant.now())
                .updatedBy(otherUser)
                .rowVersion(1)
                .build();

        when(importJobRepository.findByIdTenantIdAndIdImportJobId(tenantId, importJobId))
                .thenReturn(Optional.of(job));
        doThrow(new UnauthorizedException("No permission"))
                .when(permissionEvaluatorService).authorize(any(), eq("USER_BULK_UPLOAD"), any(), any());

        assertThatThrownBy(() -> controller.getImportJob(tenantId, importJobId, auth))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> controller.listImportErrors(tenantId, importJobId, auth))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("H10: Creator can read import job and errors directly without re-authorizing")
    void testGetImportJob_Creator_Allowed() {
        UUID importJobId = UUID.randomUUID();
        ImportJob job = ImportJob.builder()
                .id(new ImportJobId(tenantId, importJobId))
                .uploadedBy(userId)
                .status("VALIDATED")
                .totalRows(1)
                .validRows(1)
                .errorRows(0)
                .createdAt(Instant.now())
                .createdBy(userId)
                .updatedAt(Instant.now())
                .updatedBy(userId)
                .rowVersion(1)
                .build();

        when(importJobRepository.findByIdTenantIdAndIdImportJobId(tenantId, importJobId))
                .thenReturn(Optional.of(job));
        when(importRowErrorRepository.findByIdTenantIdAndImportJobIdOrderByRowNumberAsc(tenantId, importJobId))
                .thenReturn(List.of());

        ResponseEntity<ImportJobResponse> jobRes = controller.getImportJob(tenantId, importJobId, auth);
        assertThat(jobRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<ImportRowErrorResponse>> errRes = controller.listImportErrors(tenantId, importJobId, auth);
        assertThat(errRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
