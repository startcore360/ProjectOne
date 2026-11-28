package com.edu360.api.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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

    @Test
    void grievanceEvidenceDownloadRequiresTheCaseConfidentialityPredicate() {
        UUID tenantId=UUID.randomUUID(), userId=UUID.randomUUID(), institutionId=UUID.randomUUID();
        UUID caseId=UUID.randomUUID(), fileId=UUID.randomUUID();
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),any(Object[].class))).thenReturn(Boolean.TRUE);
        JdbcFileAccessPolicy policy=new JdbcFileAccessPolicy(jdbc);
        TenantPrincipal actor=new TenantPrincipal(tenantId,userId,UserType.STAFF,"ICC",false);
        FileObject file=FileObject.builder().id(new FileObjectId(tenantId,fileId))
                .institutionId(institutionId).purpose("GRIEVANCE_EVIDENCE").status("ACTIVE")
                .scanStatus("CLEAN").uploadedBy(UUID.randomUUID()).build();

        assertThat(policy.canAccess(file,actor)).isTrue();
        org.mockito.ArgumentCaptor<String> sql=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(),eq(Boolean.class),any(Object[].class));
        assertThat(sql.getValue()).contains("app.grievance_case","app.fn_complaint_guard");
    }

    @Test
    void grievanceEvidenceUploadRequiresCaseConfidentialityAndUploaderOwnership() {
        UUID tenantId=UUID.randomUUID(), userId=UUID.randomUUID(), institutionId=UUID.randomUUID();
        UUID caseId=UUID.randomUUID(), fileId=UUID.randomUUID();
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),any(Object[].class))).thenReturn(Boolean.TRUE);
        JdbcFileAccessPolicy policy=new JdbcFileAccessPolicy(jdbc);
        TenantPrincipal actor=new TenantPrincipal(tenantId,userId,UserType.STAFF,"ICC",false);
        FileObject file=FileObject.builder().id(new FileObjectId(tenantId,fileId))
                .institutionId(institutionId).purpose("GRIEVANCE_EVIDENCE").status("ACTIVE")
                .scanStatus("PENDING").uploadedBy(userId).build();

        assertThat(policy.canAttach(file,actor,"GRIEVANCE_CASE",caseId)).isTrue();
        FileObject otherUploader=FileObject.builder().id(new FileObjectId(tenantId,UUID.randomUUID()))
                .institutionId(institutionId).purpose("GRIEVANCE_EVIDENCE").status("ACTIVE")
                .scanStatus("PENDING").uploadedBy(UUID.randomUUID()).build();
        assertThat(policy.canAttach(otherUploader,actor,"GRIEVANCE_CASE",caseId)).isFalse();
    }

    @Test
    void grievanceEvidenceDownloadDeniesWhenConfidentialityPredicateReturnsFalse() {
        UUID tenantId=UUID.randomUUID(), userId=UUID.randomUUID();
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),any(Object[].class))).thenReturn(Boolean.FALSE);
        JdbcFileAccessPolicy policy=new JdbcFileAccessPolicy(jdbc);
        TenantPrincipal actor=new TenantPrincipal(tenantId,userId,UserType.STAFF,"Generic admin",false);
        FileObject file=FileObject.builder().id(new FileObjectId(tenantId,UUID.randomUUID()))
                .institutionId(UUID.randomUUID()).purpose("GRIEVANCE_EVIDENCE").status("ACTIVE")
                .scanStatus("CLEAN").uploadedBy(UUID.randomUUID()).build();
        assertThat(policy.canAccess(file,actor)).isFalse();
    }

    // ------------------------------------------------------------ STAFF_ATTENDANCE_SELFIE

    private final UUID selfieTenant = UUID.randomUUID();
    private final UUID selfieOwner = UUID.randomUUID();
    private final UUID selfieInstitution = UUID.randomUUID();
    private final UUID selfieFile = UUID.randomUUID();

    private FileObject selfie() {
        return FileObject.builder().id(new FileObjectId(selfieTenant, selfieFile)).institutionId(selfieInstitution)
                .purpose("STAFF_ATTENDANCE_SELFIE").status("ACTIVE").scanStatus("CLEAN").uploadedBy(selfieOwner).build();
    }

    /** Makes the check-in lookup return one linked row (or none when linked is false). */
    @SuppressWarnings("unchecked")
    private JdbcTemplate jdbcWithLinkedCheckin(boolean linked, UUID approvalRequestId) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class),
                eq(selfieTenant), eq(selfieFile))).thenAnswer(invocation -> {
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.next()).thenReturn(linked);
                    when(rs.getObject(1, UUID.class)).thenReturn(selfieOwner);
                    when(rs.getObject(2, UUID.class)).thenReturn(selfieInstitution);
                    when(rs.getObject(3, UUID.class)).thenReturn(approvalRequestId);
                    return ((org.springframework.jdbc.core.ResultSetExtractor<Object>) invocation.getArgument(1)).extractData(rs);
                });
        return jdbc;
    }

    private PermissionEvaluatorService reviewPermission(UUID caller, boolean allowed) {
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        RbacPrincipal rbac = new RbacPrincipal(selfieTenant, caller);
        when(permissions.targetScope(rbac, selfieInstitution, null)).thenReturn(RbacScope.institution(selfieInstitution));
        when(permissions.can(rbac, "STAFF_ATTENDANCE_CHECKIN_REVIEW", RbacScope.institution(selfieInstitution),
                RbacTarget.of(selfieOwner))).thenReturn(allowed ? PermissionDecision.allow()
                        : PermissionDecision.deny("ACTION_NOT_GRANTED", "Denied"));
        return permissions;
    }

    @Test
    void selfie_ownerCanRead_withoutAnyLookup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantPrincipal owner = new TenantPrincipal(selfieTenant, selfieOwner, UserType.STAFF, "Owner", false);

        assertThat(new JdbcFileAccessPolicy(jdbc).canAccess(selfie(), owner)).isTrue();
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    @Test
    void selfie_notYetLinkedToACheckin_isTheOwnersAlone() {
        UUID other = UUID.randomUUID();
        JdbcFileAccessPolicy policy = new JdbcFileAccessPolicy(jdbcWithLinkedCheckin(false, null));
        PermissionEvaluatorService permissions = reviewPermission(other, true);
        ReflectionTestUtils.setField(policy, "permissions", permissions);

        assertThat(policy.canAccess(selfie(), new TenantPrincipal(selfieTenant, other, UserType.STAFF, "Principal", false)))
                .isFalse();
        verify(permissions, org.mockito.Mockito.never()).can(any(), anyString(), any(), any());
    }

    @Test
    void selfie_peerStaffWithoutRouteOrReviewAction_isDenied() {
        UUID peer = UUID.randomUUID();
        JdbcFileAccessPolicy policy = new JdbcFileAccessPolicy(jdbcWithLinkedCheckin(true, null));
        ReflectionTestUtils.setField(policy, "permissions", reviewPermission(peer, false));

        assertThat(policy.canAccess(selfie(), new TenantPrincipal(selfieTenant, peer, UserType.STAFF, "Peer", false)))
                .isFalse();
    }

    @Test
    void selfie_reviewHolderAtTheCheckinInstitution_canRead() {
        UUID principalUser = UUID.randomUUID();
        JdbcFileAccessPolicy policy = new JdbcFileAccessPolicy(jdbcWithLinkedCheckin(true, null));
        PermissionEvaluatorService permissions = reviewPermission(principalUser, true);
        ReflectionTestUtils.setField(policy, "permissions", permissions);

        assertThat(policy.canAccess(selfie(),
                new TenantPrincipal(selfieTenant, principalUser, UserType.STAFF, "Principal", false))).isTrue();
        verify(permissions).can(new RbacPrincipal(selfieTenant, principalUser), "STAFF_ATTENDANCE_CHECKIN_REVIEW",
                RbacScope.institution(selfieInstitution), RbacTarget.of(selfieOwner));
    }

    @Test
    void selfie_routedApproverOfThePendingStep_canRead_evenWithoutInstitutionWideReview() {
        UUID hod = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        JdbcFileAccessPolicy policy = new JdbcFileAccessPolicy(jdbcWithLinkedCheckin(true, approvalId));
        PermissionEvaluatorService permissions = reviewPermission(hod, false);
        com.edu360.api.approval.ApprovalRequestRepository requests = mock(com.edu360.api.approval.ApprovalRequestRepository.class);
        com.edu360.api.approval.ApprovalStepRepository steps = mock(com.edu360.api.approval.ApprovalStepRepository.class);
        com.edu360.api.approval.ApprovalRouting routing = mock(com.edu360.api.approval.ApprovalRouting.class);
        com.edu360.api.approval.ApprovalRequest request = com.edu360.api.approval.ApprovalRequest.builder()
                .id(new com.edu360.api.approval.ApprovalRequestId(selfieTenant, approvalId))
                .processCode("STAFF_ATTENDANCE_REVIEW").status("PENDING").currentStepNo((short) 1)
                .requestedByUserId(selfieOwner).build();
        com.edu360.api.approval.ApprovalStep step = com.edu360.api.approval.ApprovalStep.builder()
                .approvalRequestId(approvalId).stepNo((short) 1).approverRoleCode("HEAD_OF_DEPARTMENT")
                .approverUserId(hod).decision("PENDING").build();
        when(requests.findByIdTenantIdAndIdApprovalRequestId(selfieTenant, approvalId)).thenReturn(java.util.Optional.of(request));
        when(steps.findByIdTenantIdAndApprovalRequestIdAndStepNo(selfieTenant, approvalId, (short) 1))
                .thenReturn(java.util.Optional.of(step));
        when(routing.route(eq(selfieTenant), eq(hod), any(), eq(request), eq(step))).thenReturn(java.util.Optional.of(
                new com.edu360.api.approval.ApprovalRouting.Route(com.edu360.api.approval.ApprovalRouting.Via.USER, null, null, null)));
        // The HOD holds the review action at the check-in's department scope (what the engine checks).
        UUID department = UUID.randomUUID();
        RbacScope departmentScope = RbacScope.department(selfieInstitution, department);
        com.edu360.api.approval.ApprovalScopeResolver scopes = mock(com.edu360.api.approval.ApprovalScopeResolver.class);
        when(scopes.scopeFor(new RbacPrincipal(selfieTenant, hod), request)).thenReturn(departmentScope);
        when(permissions.can(new RbacPrincipal(selfieTenant, hod), "STAFF_ATTENDANCE_CHECKIN_REVIEW", departmentScope,
                RbacTarget.of(selfieOwner))).thenReturn(PermissionDecision.allow());
        ReflectionTestUtils.setField(policy, "permissions", permissions);
        ReflectionTestUtils.setField(policy, "approvalRequests", requests);
        ReflectionTestUtils.setField(policy, "approvalSteps", steps);
        ReflectionTestUtils.setField(policy, "approvalRouting", routing);
        ReflectionTestUtils.setField(policy, "approvalScopeResolver", scopes);

        assertThat(policy.canAccess(selfie(), new TenantPrincipal(selfieTenant, hod, UserType.STAFF, "HOD", false))).isTrue();

        // Once the step is decided the HOD is no longer the routed approver: back to denied.
        step.setDecision("APPROVED");
        assertThat(policy.canAccess(selfie(), new TenantPrincipal(selfieTenant, hod, UserType.STAFF, "HOD", false))).isFalse();
    }

    @Test
    void selfie_hodRoutedByRoleButOfAnotherDepartment_isDenied() {
        UUID otherHod = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        JdbcFileAccessPolicy policy = new JdbcFileAccessPolicy(jdbcWithLinkedCheckin(true, approvalId));
        PermissionEvaluatorService permissions = reviewPermission(otherHod, false);
        com.edu360.api.approval.ApprovalRequestRepository requests = mock(com.edu360.api.approval.ApprovalRequestRepository.class);
        com.edu360.api.approval.ApprovalStepRepository steps = mock(com.edu360.api.approval.ApprovalStepRepository.class);
        com.edu360.api.approval.ApprovalRouting routing = mock(com.edu360.api.approval.ApprovalRouting.class);
        com.edu360.api.approval.ApprovalRequest request = com.edu360.api.approval.ApprovalRequest.builder()
                .id(new com.edu360.api.approval.ApprovalRequestId(selfieTenant, approvalId))
                .processCode("STAFF_ATTENDANCE_REVIEW").status("PENDING").currentStepNo((short) 1)
                .requestedByUserId(selfieOwner).build();
        com.edu360.api.approval.ApprovalStep step = com.edu360.api.approval.ApprovalStep.builder()
                .approvalRequestId(approvalId).stepNo((short) 1).approverRoleCode("HEAD_OF_DEPARTMENT")
                .decision("PENDING").build();
        when(requests.findByIdTenantIdAndIdApprovalRequestId(selfieTenant, approvalId)).thenReturn(java.util.Optional.of(request));
        when(steps.findByIdTenantIdAndApprovalRequestIdAndStepNo(selfieTenant, approvalId, (short) 1))
                .thenReturn(java.util.Optional.of(step));
        // Routed by role only (any active HOD matches the HEAD_OF_DEPARTMENT step) ...
        when(routing.route(eq(selfieTenant), eq(otherHod), any(), eq(request), eq(step))).thenReturn(java.util.Optional.of(
                new com.edu360.api.approval.ApprovalRouting.Route(com.edu360.api.approval.ApprovalRouting.Via.ROLE, null, null, null)));
        // ... but the evaluator denies at the check-in's department scope, so the selfie stays closed.
        RbacScope departmentScope = RbacScope.department(selfieInstitution, UUID.randomUUID());
        com.edu360.api.approval.ApprovalScopeResolver scopes = mock(com.edu360.api.approval.ApprovalScopeResolver.class);
        when(scopes.scopeFor(new RbacPrincipal(selfieTenant, otherHod), request)).thenReturn(departmentScope);
        when(permissions.can(new RbacPrincipal(selfieTenant, otherHod), "STAFF_ATTENDANCE_CHECKIN_REVIEW", departmentScope,
                RbacTarget.of(selfieOwner))).thenReturn(PermissionDecision.deny("NO_ACTIVE_ROLE_IN_SCOPE", "Denied"));
        ReflectionTestUtils.setField(policy, "permissions", permissions);
        ReflectionTestUtils.setField(policy, "approvalRequests", requests);
        ReflectionTestUtils.setField(policy, "approvalSteps", steps);
        ReflectionTestUtils.setField(policy, "approvalRouting", routing);
        ReflectionTestUtils.setField(policy, "approvalScopeResolver", scopes);

        assertThat(policy.canAccess(selfie(), new TenantPrincipal(selfieTenant, otherHod, UserType.STAFF, "HOD", false)))
                .isFalse();
    }
}
