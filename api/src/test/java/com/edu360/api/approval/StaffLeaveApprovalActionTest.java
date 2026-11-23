package com.edu360.api.approval;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class StaffLeaveApprovalActionTest {
    @Test
    void mapsEachStaffLeaveApprovalStepToTheActionRegisterPermission() {
        ApprovalRequest request = ApprovalRequest.builder()
                .processCode("STAFF_LEAVE").subjectType("STAFF_LEAVE_REQUEST")
                .requesterActiveRole("PROFESSOR").build();
        assertThat(ApprovalInboxService.resolveApprovalAction(request, step("HEAD_OF_DEPARTMENT")))
                .isEqualTo("STAFF_LEAVE_RECOMMEND_HOD");
        assertThat(ApprovalInboxService.resolveApprovalAction(request, step("HR_MANAGER")))
                .isEqualTo("STAFF_LEAVE_VERIFY_HR");
        assertThat(ApprovalInboxService.resolveApprovalAction(request, step("PRINCIPAL_COLLEGE")))
                .isEqualTo("STAFF_LEAVE_APPROVE_PRINCIPAL");
        assertThat(ApprovalInboxService.resolveApprovalAction(request, step("REGISTRAR")))
                .isEqualTo("STAFF_LEAVE_APPROVE_REGISTRAR");
    }

    @Test
    void principalAndSeniorLeaveUseTheirDistinctConfiguredApproverActions() {
        ApprovalRequest principalLeave = ApprovalRequest.builder()
                .processCode("STAFF_LEAVE").subjectType("STAFF_LEAVE_REQUEST")
                .requesterActiveRole("PRINCIPAL_COLLEGE").build();
        ApprovalRequest vcLeave = ApprovalRequest.builder()
                .processCode("STAFF_LEAVE").subjectType("STAFF_LEAVE_REQUEST")
                .requesterActiveRole("VICE_CHANCELLOR").build();
        ApprovalRequest registrarLeave = ApprovalRequest.builder()
                .processCode("STAFF_LEAVE").subjectType("STAFF_LEAVE_REQUEST")
                .requesterActiveRole("REGISTRAR").build();
        assertThat(ApprovalInboxService.resolveApprovalAction(principalLeave, step("MANAGEMENT")))
                .isEqualTo("PRINCIPAL_LEAVE_APPROVE");
        assertThat(ApprovalInboxService.resolveApprovalAction(vcLeave, step("MANAGEMENT")))
                .isEqualTo("SENIOR_LEAVE_APPROVE");
        assertThat(ApprovalInboxService.resolveApprovalAction(registrarLeave, step("UNIVERSITY_HR_MANAGER")))
                .isEqualTo("STAFF_LEAVE_VERIFY_HR");
        assertThat(ApprovalInboxService.resolveApprovalAction(registrarLeave, step("VICE_CHANCELLOR")))
                .isEqualTo("SENIOR_LEAVE_APPROVE");
    }

    private static ApprovalStep step(String role) {
        return ApprovalStep.builder().approverRoleCode(role).build();
    }
}
