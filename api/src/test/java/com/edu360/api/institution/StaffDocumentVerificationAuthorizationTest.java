package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.files.FileAccessPolicy;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;

class StaffDocumentVerificationAuthorizationTest {
    private record Fixture(StaffDocumentService service, StaffDocumentRepository documents,
            PermissionEvaluatorService permissions, AuditLogService audit,
            UUID tenantId, UUID verifierId, UUID documentId) {}

    private Fixture fixture(boolean staffHasActiveAssignment) {
        UUID tenantId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID staffProfileId = UUID.randomUUID();
        StaffDocumentRepository documents = mock(StaffDocumentRepository.class);
        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        AuditLogService audit = mock(AuditLogService.class);

        StaffDocument document = new StaffDocument();
        document.setId(new StaffDocumentId(tenantId, documentId));
        document.setStaffProfileId(staffProfileId);
        document.setDocumentType("DEGREE");
        document.setFileId(UUID.randomUUID());
        document.setVerificationStatus("UPLOADED");
        document.setCreatedBy(uploaderId);
        document.setUpdatedBy(uploaderId);
        when(documents.findByIdTenantIdAndIdStaffDocumentId(tenantId, documentId))
                .thenReturn(Optional.of(document));

        StaffProfile profile = new StaffProfile();
        profile.setId(new StaffProfileId(tenantId, staffProfileId));
        when(profiles.findByIdTenantIdAndIdStaffProfileId(tenantId, staffProfileId))
                .thenReturn(Optional.of(profile));

        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.TENANT);
        RbacPrincipal caller = new RbacPrincipal(tenantId, verifierId);
        when(permissions.activeAssignment(caller)).thenReturn(Optional.of(active));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(tenantId), eq(staffProfileId)))
                .thenReturn(staffHasActiveAssignment);
        when(documents.save(any(StaffDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffDocumentService service = new StaffDocumentService(profiles, documents,
                mock(FileObjectRepository.class), mock(FileAccessPolicy.class), jdbc, permissions, audit);
        return new Fixture(service, documents, permissions, audit, tenantId, verifierId, documentId);
    }

    @Test
    void authorizedTenantVerifierCanVerifyAnotherUsersDocument() {
        Fixture f = fixture(true);
        TenantPrincipal actor = new TenantPrincipal(f.tenantId(), f.verifierId(), UserType.STAFF, "Verifier", false);
        RbacPrincipal caller = new RbacPrincipal(f.tenantId(), f.verifierId());

        var response = f.service().verify(actor, f.documentId(), "VERIFIED", "Original checked");

        assertThat(response.verificationStatus()).isEqualTo("VERIFIED");
        assertThat(response.verifiedBy()).isEqualTo(f.verifierId());
        verify(f.permissions()).authorize(caller, "STAFF_DOCUMENT_VERIFY", RbacScope.tenant(), RbacTarget.NONE);
        verify(f.audit()).record(any(com.edu360.api.audit.AuditEntry.class));
    }

    @Test
    void documentOutsideVerifierActiveScopeIsNotDiscoverable() {
        Fixture f = fixture(false);
        TenantPrincipal actor = new TenantPrincipal(f.tenantId(), f.verifierId(), UserType.STAFF, "Verifier", false);

        assertThatThrownBy(() -> f.service().verify(actor, f.documentId(), "VERIFIED", null))
                .isInstanceOf(com.edu360.api.exception.ResourceNotFoundException.class);
        verify(f.documents(), never()).save(any(StaffDocument.class));
        verifyNoInteractions(f.audit());
    }
}
