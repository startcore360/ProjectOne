package com.edu360.api.platformops.dataprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.edu360.api.idempotency.PlatformIdempotencyService;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformAccessLookup;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.CreateDeletionRequestDto;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DecideDeletionRequestDto;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DeletionRequestResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

class PlatformDeletionRequestControllerTest {

    private ExportRequestRepository exportRequestRepo;
    private DeletionRequestRepository deletionRequestRepo;
    private TenantRetentionPolicyRepository retentionPolicyRepo;
    private RetentionChangeRequestRepository retentionChangeRequestRepo;
    private TenantRepository tenantRepo;
    private AuditLogService auditLogService;
    private com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter;
    private com.edu360.api.config.TenantIssuerAuthenticationManagerResolver tenantIssuerResolver;
    private com.edu360.api.platformops.PlatformNotificationService notificationService;
    private PlatformPermissionService permissionService;
    private PlatformDataProtectionService dataProtectionService;
    private PlatformDeletionRequestController controller;
    private PlatformDataProtectionController dataProtectionController;
    private PlatformIdempotencyService idempotencyService;
    private ObjectMapper objectMapper;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PO_USER_ID = UUID.randomUUID();
    private static final UUID DPO_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        exportRequestRepo = mock(ExportRequestRepository.class);
        deletionRequestRepo = mock(DeletionRequestRepository.class);
        retentionPolicyRepo = mock(TenantRetentionPolicyRepository.class);
        retentionChangeRequestRepo = mock(RetentionChangeRequestRepository.class);
        tenantRepo = mock(TenantRepository.class);
        platformAuditWriter = mock(com.edu360.api.platformops.role.PlatformAuditWriter.class);
        tenantIssuerResolver = mock(com.edu360.api.config.TenantIssuerAuthenticationManagerResolver.class);
        notificationService = mock(com.edu360.api.platformops.PlatformNotificationService.class);
        idempotencyService = mock(PlatformIdempotencyService.class);
        objectMapper = new ObjectMapper();
        auditLogService = mock(AuditLogService.class);

        PlatformPermissionService permissionService = com.edu360.api.platformops.PlatformTestSupport.permissions(PlatformAccessLookup.standardRolesOnly());
        this.permissionService = permissionService;

        dataProtectionService = new PlatformDataProtectionService(
                exportRequestRepo,
                deletionRequestRepo,
                retentionPolicyRepo,
                retentionChangeRequestRepo,
                tenantRepo,
                platformAuditWriter,
                tenantIssuerResolver,
                notificationService
        );

        controller = new PlatformDeletionRequestController(dataProtectionService, permissionService);
        dataProtectionController = new PlatformDataProtectionController(dataProtectionService, permissionService, idempotencyService, objectMapper);
    }

    private Authentication authFor(UUID userId, PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(userId, role, role.name().toLowerCase() + "@platform.edu360.local", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    @DisplayName("C8 / D1: End-to-end multi-step tenant deletion chain")
    void testDeletionChain_fullLifecycle() {
        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).tenantName("Test University").state(TenantState.ACTIVE).build();
        when(tenantRepo.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(deletionRequestRepo.save(any(DeletionRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(exportRequestRepo.save(any(ExportRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantRetentionPolicy policy = TenantRetentionPolicy.builder()
                .tenantRetentionPolicyId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .dataClassCode("ALL")
                .retentionMonths(12)
                .status("ACTIVE")
                .build();
        when(retentionPolicyRepo.findByTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(List.of(policy));

        // 1. Request Deletion (Product Owner)
        Authentication poAuth = authFor(PO_USER_ID, PlatformRole.PRODUCT_OWNER);
        CreateDeletionRequestDto createDto = new CreateDeletionRequestDto(TENANT_ID, "PRODUCT_OWNER", "Tenant closure");
        ResponseEntity<DeletionRequestResponse> reqRes = controller.requestDeletion(createDto, poAuth);

        assertThat(reqRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reqRes.getBody().status()).isEqualTo("REQUESTED");
        UUID reqId = reqRes.getBody().deletionRequestId();

        DeletionRequest savedReq = DeletionRequest.builder()
                .deletionRequestId(reqId)
                .tenantId(TENANT_ID)
                .origin("PRODUCT_OWNER")
                .requestedByUserId(PO_USER_ID)
                .status("REQUESTED")
                .createdAt(Instant.now())
                .createdBy(PO_USER_ID)
                .updatedAt(Instant.now())
                .updatedBy(PO_USER_ID)
                .rowVersion(1)
                .build();
        when(deletionRequestRepo.findById(reqId)).thenReturn(Optional.of(savedReq));

        // 2. Confirm Deletion (Product Owner)
        ResponseEntity<DeletionRequestResponse> confirmRes = controller.confirmDeletion(reqId, poAuth);
        assertThat(confirmRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmRes.getBody().status()).isEqualTo("PENDING_DPO");
        assertThat(savedReq.getPoConfirmedBy()).isEqualTo(PO_USER_ID);

        // 3. Decide Deletion - Approve (DPO)
        Authentication dpoAuth = authFor(DPO_USER_ID, PlatformRole.DATA_PROTECTION_OFFICER);
        DecideDeletionRequestDto approveDto = new DecideDeletionRequestDto("APPROVED", "Verified retention and export compliance");
        ResponseEntity<DeletionRequestResponse> decideRes = controller.decideDeletion(reqId, approveDto, dpoAuth);

        assertThat(decideRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decideRes.getBody().status()).isEqualTo("APPROVED");
        assertThat(tenant.getState()).isEqualTo(TenantState.DELETED);
    }

    @Test
    @DisplayName("C8 / D1: Product Owner cannot decide tenant deletion (DPO only)")
    void testProductOwner_cannotApproveDeletion() {
        Authentication poAuth = authFor(PO_USER_ID, PlatformRole.PRODUCT_OWNER);
        DecideDeletionRequestDto approveDto = new DecideDeletionRequestDto("APPROVED", "Self-decision attempt");

        assertThatThrownBy(() -> controller.decideDeletion(UUID.randomUUID(), approveDto, poAuth))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("C8 / D1: Same person cannot confirm and decide (ck_deletion_request_r4)")
    void testSamePersonCannotConfirmAndDecide() {
        UUID reqId = UUID.randomUUID();
        DeletionRequest savedReq = DeletionRequest.builder()
                .deletionRequestId(reqId)
                .tenantId(TENANT_ID)
                .origin("PRODUCT_OWNER")
                .requestedByUserId(DPO_USER_ID)
                .poConfirmedBy(DPO_USER_ID) // Confirmed by DPO_USER_ID
                .status("PENDING_DPO")
                .createdAt(Instant.now())
                .createdBy(DPO_USER_ID)
                .updatedAt(Instant.now())
                .updatedBy(DPO_USER_ID)
                .rowVersion(2)
                .build();
        when(deletionRequestRepo.findById(reqId)).thenReturn(Optional.of(savedReq));

        Authentication dpoAuth = authFor(DPO_USER_ID, PlatformRole.DATA_PROTECTION_OFFICER);
        DecideDeletionRequestDto approveDto = new DecideDeletionRequestDto("APPROVED", "ok");

        assertThatThrownBy(() -> controller.decideDeletion(reqId, approveDto, dpoAuth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Product Owner who confirmed cannot decide");
    }

    @Test
    @DisplayName("C8 / D1: Rejection requires mandatory remark")
    void testRejectionRequiresRemark() {
        UUID reqId = UUID.randomUUID();
        DeletionRequest savedReq = DeletionRequest.builder()
                .deletionRequestId(reqId)
                .tenantId(TENANT_ID)
                .origin("PRODUCT_OWNER")
                .requestedByUserId(PO_USER_ID)
                .poConfirmedBy(PO_USER_ID)
                .status("PENDING_DPO")
                .createdAt(Instant.now())
                .createdBy(PO_USER_ID)
                .updatedAt(Instant.now())
                .updatedBy(PO_USER_ID)
                .rowVersion(2)
                .build();
        when(deletionRequestRepo.findById(reqId)).thenReturn(Optional.of(savedReq));

        Authentication dpoAuth = authFor(DPO_USER_ID, PlatformRole.DATA_PROTECTION_OFFICER);
        DecideDeletionRequestDto rejectDto = new DecideDeletionRequestDto("REJECTED", "   ");

        assertThatThrownBy(() -> controller.decideDeletion(reqId, rejectDto, dpoAuth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Rejection remark is mandatory");
    }

    @Test
    @DisplayName("C8 / D1: Step out of order is refused")
    void testStepOutOfOrderRefused() {
        UUID reqId = UUID.randomUUID();
        DeletionRequest savedReq = DeletionRequest.builder()
                .deletionRequestId(reqId)
                .tenantId(TENANT_ID)
                .origin("PRODUCT_OWNER")
                .status("REQUESTED") // Still in REQUESTED, not PENDING_DPO
                .rowVersion(1)
                .build();
        when(deletionRequestRepo.findById(reqId)).thenReturn(Optional.of(savedReq));

        Authentication dpoAuth = authFor(DPO_USER_ID, PlatformRole.DATA_PROTECTION_OFFICER);
        DecideDeletionRequestDto approveDto = new DecideDeletionRequestDto("APPROVED", "premature");

        assertThatThrownBy(() -> controller.decideDeletion(reqId, approveDto, dpoAuth))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("must be in PENDING_DPO status to decide");
    }
    @Test
    @DisplayName("E0-B13: DPO approval marks request APPROVED, audits and notifies without starting export")
    void exportApprovalOnlyChangesStatusAuditsAndNotifiesRequester() {
        UUID exportId = UUID.randomUUID();
        ExportRequest request = ExportRequest.builder()
                .exportRequestId(exportId)
                .tenantId(TENANT_ID)
                .requestedByUserId(PO_USER_ID)
                .requestedByKind("PRODUCT_OWNER")
                .status("REQUESTED")
                .createdAt(Instant.now())
                .createdBy(PO_USER_ID)
                .updatedAt(Instant.now())
                .updatedBy(PO_USER_ID)
                .rowVersion(1)
                .build();
        when(exportRequestRepo.findById(exportId)).thenReturn(Optional.of(request));
        when(exportRequestRepo.save(any(ExportRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = dataProtectionController.decideExportRequest(
                exportId, new DecideDeletionRequestDto("APPROVED", null),
                authFor(DPO_USER_ID, PlatformRole.DATA_PROTECTION_OFFICER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("APPROVED");
        assertThat(request.getFileRef()).isNull();
        assertThat(request.getDownloadExpiresAt()).isNull();
        verify(platformAuditWriter).record(any(PlatformPrincipal.class), eq(TENANT_ID),
                eq("TENANT_EXPORT_APPROVED"), eq("APPROVE"), eq("TENANT_EXPORT_APPROVE"),
                eq("EXPORT_REQUEST"), eq(exportId), eq(com.edu360.api.platformops.role.PlatformAuditWriter.SUCCESS));
        verify(notificationService).notify(eq(PO_USER_ID), eq("EXPORT_REQUEST_APPROVED"),
                eq("Tenant export approved"),
                eq("Your export request was approved. The export worker will pick up this APPROVED request."),
                eq("EXPORT_REQUEST"), eq(exportId), eq("NORMAL"), eq(DPO_USER_ID));
    }

    @Test
    @DisplayName("E0-B13: DPO export queue filters by status and tenant")
    void exportQueueFiltersByStatusAndTenant() {
        ExportRequest row = ExportRequest.builder()
                .exportRequestId(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .requestedByUserId(PO_USER_ID)
                .requestedByKind("PRODUCT_OWNER")
                .status("APPROVED")
                .createdAt(Instant.now())
                .createdBy(PO_USER_ID)
                .updatedAt(Instant.now())
                .updatedBy(PO_USER_ID)
                .rowVersion(1)
                .build();
        when(exportRequestRepo.findByStatusAndTenantIdOrderByCreatedAtDesc("APPROVED", TENANT_ID))
                .thenReturn(List.of(row));

        var response = dataProtectionController.listAllExports(" approved ", TENANT_ID,
                authFor(DPO_USER_ID, PlatformRole.DATA_PROTECTION_OFFICER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).status()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("D5: tenant export request stores and completes the platform idempotency claim")
    void exportRequestUsesPlatformIdempotencyKey() {
        when(tenantRepo.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder().tenantId(TENANT_ID).tenantName("Test University").state(TenantState.ACTIVE).build()));
        when(exportRequestRepo.save(any(ExportRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID claimId = UUID.randomUUID();
        when(idempotencyService.claim(eq(PO_USER_ID), eq("requestTenantExportPlatform"),
                eq("export-key-123"), any())).thenReturn(
                        new PlatformIdempotencyService.Claim(claimId, false, null, null));

        ResponseEntity<com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.ExportRequestResponse> response =
                dataProtectionController.exportTenant(TENANT_ID, "export-key-123",
                        authFor(PO_USER_ID, PlatformRole.PRODUCT_OWNER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("REQUESTED");
        verify(idempotencyService).complete(eq(PO_USER_ID), eq(claimId), eq(HttpStatus.OK.value()), any());
    }

    @Test
    @DisplayName("D5: completed tenant export request replays without creating a second request")
    void exportRequestReplaysStoredResponse() {
        var stored = new com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.ExportRequestResponse(
                UUID.randomUUID(), TENANT_ID, "PRODUCT_OWNER", "REQUESTED", null, null, Instant.now());
        UUID claimId = UUID.randomUUID();
        when(idempotencyService.claim(eq(PO_USER_ID), eq("requestTenantExportPlatform"),
                eq("export-key-123"), any())).thenReturn(new PlatformIdempotencyService.Claim(
                        claimId, true, HttpStatus.OK.value(), objectMapper.valueToTree(stored)));

        var response = dataProtectionController.exportTenant(TENANT_ID, "export-key-123",
                authFor(PO_USER_ID, PlatformRole.PRODUCT_OWNER));

        assertThat(response.getBody()).isEqualTo(stored);
        verifyNoInteractions(exportRequestRepo);
        verify(idempotencyService, never()).complete(any(), any(), anyInt(), any());
    }


}
