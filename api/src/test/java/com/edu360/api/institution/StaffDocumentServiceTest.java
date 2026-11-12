package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.files.FileAccessPolicy;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import org.springframework.jdbc.core.JdbcTemplate;

class StaffDocumentServiceTest {
    @Test
    void uploaderCannotVerifyOwnStaffDocument() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        StaffDocumentRepository documents = mock(StaffDocumentRepository.class);
        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        FileObjectRepository files = mock(FileObjectRepository.class);
        FileAccessPolicy accessPolicy = mock(FileAccessPolicy.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        AuditLogService audit = mock(AuditLogService.class);
        StaffDocument document = new StaffDocument();
        document.setVerificationStatus("UPLOADED");
        document.setCreatedBy(userId);
        when(documents.findByIdTenantIdAndIdStaffDocumentId(tenantId, documentId))
                .thenReturn(Optional.of(document));
        StaffDocumentService service = new StaffDocumentService(profiles, documents, files,
                accessPolicy, jdbc, permissions, audit);
        TenantPrincipal actor = new TenantPrincipal(tenantId, userId, UserType.STAFF, "Uploader", false);

        assertThatThrownBy(() -> service.verify(actor, documentId, "VERIFIED", ""))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("uploader cannot verify");
        verifyNoInteractions(profiles, files, accessPolicy, permissions, audit);
    }

    @Test
    void rejectsUnsupportedVerificationDecisionBeforeRepositoryLookup() {
        StaffDocumentRepository documents = mock(StaffDocumentRepository.class);
        StaffDocumentService service = new StaffDocumentService(mock(StaffProfileRepository.class),
                documents, mock(FileObjectRepository.class), mock(FileAccessPolicy.class),
                mock(JdbcTemplate.class), mock(PermissionEvaluatorService.class), mock(AuditLogService.class));
        TenantPrincipal actor = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                UserType.STAFF, "Verifier", false);

        assertThatThrownBy(() -> service.verify(actor, UUID.randomUUID(), "PENDING", null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("VERIFIED or REJECTED");
        verifyNoInteractions(documents);
    }
}
