package com.edu360.api.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionDecision;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import org.springframework.test.util.ReflectionTestUtils;

class JdbcFileAccessPolicyTest {
    @Test
    void uploaderCanAttachPendingScanStaffDocumentToScopedStaffRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID staffProfileId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
                .thenReturn(Boolean.TRUE);
        JdbcFileAccessPolicy policy = new JdbcFileAccessPolicy(jdbc);
        TenantPrincipal actor = new TenantPrincipal(tenantId, userId, UserType.STAFF, "HR", false);
        FileObject file = FileObject.builder()
                .id(new FileObjectId(tenantId, fileId))
                .institutionId(institutionId)
                .purpose("STAFF_DOCUMENT")
                .status("ACTIVE")
                .scanStatus("PENDING")
                .uploadedBy(userId)
                .build();

        assertThat(policy.canAttach(file, actor, "STAFF_DOCUMENT", staffProfileId)).isTrue();
    }

    @Test
    void refusesPendingStaffDocumentUploadedByAnotherUser() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileObject file = FileObject.builder()
                .id(new FileObjectId(tenantId, UUID.randomUUID()))
                .institutionId(UUID.randomUUID())
                .purpose("STAFF_DOCUMENT")
                .status("ACTIVE")
                .scanStatus("PENDING")
                .uploadedBy(UUID.randomUUID())
                .build();
        TenantPrincipal actor = new TenantPrincipal(tenantId, userId, UserType.STAFF, "HR", false);

        assertThat(new JdbcFileAccessPolicy(mock(JdbcTemplate.class))
                .canAttach(file, actor, "STAFF_DOCUMENT", UUID.randomUUID())).isFalse();
    }


    @Test
    void institutionAuthorizedStaffCanViewStudyMaterialsBeyondTheirOwnUploads() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(Boolean.FALSE);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        when(permissions.can(new RbacPrincipal(tenantId, userId), "STUDY_MATERIAL_VIEW",
                RbacScope.institution(institutionId), RbacTarget.NONE)).thenReturn(PermissionDecision.allow());
        JdbcFileAccessPolicy policy = new JdbcFileAccessPolicy(jdbc);
        ReflectionTestUtils.setField(policy, "permissions", permissions);
        TenantPrincipal actor = new TenantPrincipal(tenantId, userId, UserType.STAFF, "Principal", false);
        FileObject file = FileObject.builder()
                .id(new FileObjectId(tenantId, fileId))
                .institutionId(institutionId)
                .purpose("STUDY_MATERIAL")
                .status("ACTIVE")
                .scanStatus("CLEAN")
                .uploadedBy(UUID.randomUUID())
                .build();

        assertThat(policy.canAccess(file, actor)).isTrue();
        verify(permissions).can(new RbacPrincipal(tenantId, userId), "STUDY_MATERIAL_VIEW",
                RbacScope.institution(institutionId), RbacTarget.NONE);
    }

    @Test
    void studentCannotUseStaffStudyMaterialAccessFallback() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(Boolean.FALSE);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        JdbcFileAccessPolicy policy = new JdbcFileAccessPolicy(jdbc);
        ReflectionTestUtils.setField(policy, "permissions", permissions);
        TenantPrincipal actor = new TenantPrincipal(tenantId, userId, UserType.STUDENT, "Student", false);
        FileObject file = FileObject.builder()
                .id(new FileObjectId(tenantId, UUID.randomUUID()))
                .institutionId(institutionId)
                .purpose("STUDY_MATERIAL")
                .status("ACTIVE")
                .scanStatus("CLEAN")
                .uploadedBy(UUID.randomUUID())
                .build();

        assertThat(policy.canAccess(file, actor)).isFalse();
        verify(permissions, org.mockito.Mockito.never()).can(any(), anyString(), any(), any());
    }

    @Test
    void refusesInfectedOrQuarantinedStaffDocument() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileObject file = FileObject.builder()
                .id(new FileObjectId(tenantId, UUID.randomUUID()))
                .institutionId(UUID.randomUUID())
                .purpose("STAFF_DOCUMENT")
                .status("QUARANTINED")
                .scanStatus("INFECTED")
                .uploadedBy(userId)
                .build();
        TenantPrincipal actor = new TenantPrincipal(tenantId, userId, UserType.STAFF, "HR", false);

        assertThat(new JdbcFileAccessPolicy(mock(JdbcTemplate.class))
                .canAttach(file, actor, "STAFF_DOCUMENT", UUID.randomUUID())).isFalse();
    }
}
