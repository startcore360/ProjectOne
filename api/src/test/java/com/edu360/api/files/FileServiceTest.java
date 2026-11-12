package com.edu360.api.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {
    @Mock FileObjectRepository repository;
    @Mock LocalFileStorage storage;
    @Mock PermissionEvaluatorService permissions;
    @Mock FileScanner scanner;
    @Mock FileAccessPolicy accessPolicy;
    @Mock AuditLogService auditLog;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final TenantPrincipal principal = new TenantPrincipal(tenantId, userId, UserType.STAFF, "Uploader", false);
    private FileService service;

    @BeforeEach
    void setUp() {
        FileContentInspector inspector = new FileContentInspector();
        service = new FileService(repository, storage, permissions, inspector, scanner, accessPolicy, auditLog, true);
    }

    private void allowPermission() {
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.INSTITUTION);
        when(active.getInstitutionId()).thenReturn(UUID.randomUUID());
        when(permissions.activeAssignment(any())).thenReturn(Optional.of(active));
    }

    private void allowUpload() {
        allowPermission();
        when(scanner.scan(any())).thenReturn(FileScanner.ScanResult.PENDING);
        when(storage.store(eq(tenantId), any(UUID.class), any())).thenReturn("tenants/file.pdf");
        when(repository.save(any(FileObject.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void S2_27_rejectsExecutableRenamedWithPdfSuffix() {
        allowPermission();
        MockMultipartFile upload = new MockMultipartFile("file", "report.pdf.exe",
                "application/x-msdownload", new byte[]{0x4d, 0x5a, 0, 0});
        assertThatThrownBy(() -> service.upload(principal, upload, "STUDY_MATERIAL", null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("permitted safe file types");
        verify(repository, never()).save(any());
        verify(auditLog, never()).record(any(AuditEntry.class));
    }

    @Test
    void S2_27_rejectsTwelveMiBUploadWithPayloadTooLarge() {
        MockMultipartFile upload = new MockMultipartFile("file", "large.pdf", "application/pdf",
                new byte[12 * 1024 * 1024]);
        assertThatThrownBy(() -> service.upload(principal, upload, "STUDY_MATERIAL", null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("10 MiB");
        verify(permissions, never()).activeAssignment(any());
    }

    @Test
    void successfulUploadWritesOneStructuredAuditRecord() {
        allowUpload();
        MockMultipartFile upload = new MockMultipartFile("file", "lesson.pdf", "application/pdf",
                "%PDF-1.7 sample".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        FileUploadResponse response = service.upload(principal, upload, "STUDY_MATERIAL", null);
        assertThat(response.scanStatus()).isEqualTo("PENDING");
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditLog, times(1)).record(audit.capture());
        assertThat(audit.getValue().eventType().name()).isEqualTo("FILE_UPLOADED");
        assertThat(audit.getValue().entityType()).isEqualTo("FILE_OBJECT");
        assertThat(audit.getValue().entityId()).isEqualTo(response.fileId());
    }

    @Test
    void S2_29_deniesClassmatesSubmissionWhenOwningRecordPolicyDoesNotMatch() {
        UUID fileId = UUID.randomUUID();
        FileObject file = FileObject.builder()
                .id(new FileObjectId(tenantId, fileId)).purpose("ASSIGNMENT_SUBMISSION")
                .storageKey("tenants/submission.pdf").originalFilename("submission.pdf")
                .contentType("application/pdf").sizeBytes(8).sha256("hash")
                .scanStatus("PENDING").status("ACTIVE").uploadedBy(UUID.randomUUID())
                .createdBy(UUID.randomUUID()).updatedBy(UUID.randomUUID()).build();
        when(repository.findById(new FileObjectId(tenantId, fileId))).thenReturn(Optional.of(file));
        when(accessPolicy.canAccess(file, principal)).thenReturn(false);
        assertThatThrownBy(() -> service.download(principal, fileId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("owning record");
        verify(permissions, never()).authorize(any(), any(), any(), any());
        verify(storage, never()).openRead(any());
    }
}
