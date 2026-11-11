package com.edu360.api.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.guardian.GuardianLinkController;
import com.edu360.api.guardian.dto.GuardianChangeRequestDto;
import com.edu360.api.guardian.dto.LinkGuardianRequest;
import com.edu360.api.identity.StudentProfile;
import com.edu360.api.identity.UserType;
import com.edu360.api.guardian.dto.GuardianChildResponse;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.TenantContext;

/** Guardian link controller over the real services and evaluator (R3); the institution comes from the student. */
class GuardianLinkControllerTest {

    private ApprovalTestWorld t;
    private GuardianLinkController controller;
    private UUID inst;
    private UUID admin;
    private StudentProfile student;

    @BeforeEach
    void setUp() {
        t = new ApprovalTestWorld();
        controller = new GuardianLinkController(t.guardian);
        inst = t.institution("COLLEGE", "ACTIVE");
        admin = t.staff("College Admin", "COLLEGE_ADMIN", ScopeType.INSTITUTION, inst);
        student = t.student(inst);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private LinkGuardianRequest guardian(String mobile) {
        t.guardianAccount(mobile);
        return new LinkGuardianRequest("Guardian", mobile, null, "FATHER", false);
    }

    @Test
    @DisplayName("SCOPE: guardian child switcher returns only the authenticated guardian's active children")
    void scope_guardianSeesOwnActiveChildren() {
        StudentProfile child = t.student(inst);
        child.setStudentCode("STU-001");
        t.activeChildren.add(child);
        TenantPrincipal guardian = new TenantPrincipal(t.tenantId, UUID.randomUUID(), UserType.GUARDIAN, "Parent", false);
        var response = controller.listActiveChildren(t.tenantId, t.auth(guardian));
        assertThat(response.getBody()).containsExactly(new GuardianChildResponse(
                child.getStudentProfileId(), inst, "STU-001", "ACTIVE"));
    }

    @Test
    @DisplayName("DENY: non-guardian account cannot enumerate linked children")
    void deny_nonGuardianCannotListChildren() {
        assertThatThrownBy(() -> controller.listActiveChildren(t.tenantId, t.auth(t.as(admin))))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ALLOW: the admin links a guardian to a student of their institution; state and audit saved")
    void allow_adminLinksGuardian() {
        Authentication auth = t.auth(t.as(admin));
        var res = controller.linkGuardian(t.tenantId, student.getStudentProfileId(), guardian("+919876543210"), auth);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(t.links.size()).isEqualTo(1);
        assertThat(t.audited("Linked guardian")).isTrue();
    }

    @Test
    @DisplayName("GUARD: more than three guardians is prohibited")
    void guard_maxThree() {
        Authentication auth = t.auth(t.as(admin));
        for (int i = 0; i < 3; i++) {
            controller.linkGuardian(t.tenantId, student.getStudentProfileId(), guardian("+91987654321" + i), auth);
        }
        assertThatThrownBy(() -> controller.linkGuardian(t.tenantId, student.getStudentProfileId(), guardian("+919876543299"), auth))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(t.links.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("GUARD: a change request without a reason is prohibited")
    void guard_reasonRequired() {
        Authentication auth = t.auth(t.as(admin));
        var link = controller.linkGuardian(t.tenantId, student.getStudentProfileId(), guardian("+919876543210"), auth).getBody();
        assertThatThrownBy(() -> controller.requestChange(t.tenantId, link.studentGuardianLinkId(), new GuardianChangeRequestDto(" "), auth))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("DENY: a user without the action is refused")
    void deny_withoutPermission() {
        UUID studentUser = t.staff("Student", "STUDENT_COLLEGE", ScopeType.INSTITUTION, inst);
        Authentication auth = t.auth(t.as(studentUser));
        assertThatThrownBy(() -> controller.linkGuardian(t.tenantId, student.getStudentProfileId(), guardian("+919876543210"), auth))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(t.links.size()).isZero();
    }

    @Test
    @DisplayName("SCOPE: a tenant mismatch is rejected")
    void scope_tenantMismatch() {
        Authentication auth = t.auth(t.as(admin));
        assertThatThrownBy(() -> controller.linkGuardian(UUID.randomUUID(), student.getStudentProfileId(), guardian("+919876543210"), auth))
                .isInstanceOf(UnauthorizedException.class);
    }
}
