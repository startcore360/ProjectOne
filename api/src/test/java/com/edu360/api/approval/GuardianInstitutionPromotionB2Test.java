package com.edu360.api.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.edu360.api.academic.PromotionDecisionRequest;
import com.edu360.api.academic.PromotionRun;
import com.edu360.api.academic.PromotionService;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.SelfApprovalForbiddenException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.guardian.GuardianLinkController;
import com.edu360.api.guardian.StudentGuardianLink;
import com.edu360.api.guardian.dto.GuardianChangeDecisionRequest;
import com.edu360.api.guardian.dto.GuardianChangeRequestDto;
import com.edu360.api.identity.StudentProfile;
import com.edu360.api.institution.InstitutionController;
import com.edu360.api.institution.dto.BlockDecisionRequest;
import com.edu360.api.institution.dto.BlockInstitutionRequest;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.TenantContext;

/**
 * B2 / P-02 (guardian links), P-03 (institution block and activation chain), P-04 (promotion
 * approval). Real evaluator and real services over in-memory stores (R3).
 */
class GuardianInstitutionPromotionB2Test {

    private ApprovalTestWorld t;
    private UUID college;
    private UUID otherCollege;
    private UUID school;
    private UUID adminCollege;
    private UUID principalCollege;
    private StudentProfile collegeStudent;

    @BeforeEach
    void setUp() {
        t = new ApprovalTestWorld();
        college = t.institution("COLLEGE", "ACTIVE");
        otherCollege = t.institution("COLLEGE", "ACTIVE");
        school = t.institution("SCHOOL", "ACTIVE");
        adminCollege = t.staff("College Admin", "COLLEGE_ADMIN", ScopeType.INSTITUTION, college);
        principalCollege = t.staff("Principal", "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, college);
        collegeStudent = t.student(college);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------------------- P-02

    @Test
    @DisplayName("ACT-GUARDIAN_LINK_VIEW-ALLOW: the College Admin lists the links of a student of their institution")
    void actGuardianLinkView_allow() {
        StudentGuardianLink l = t.link(collegeStudent.getStudentProfileId());
        var list = t.guardian.listGuardianLinks(t.tenantId, collegeStudent.getStudentProfileId(), t.as(adminCollege));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).studentGuardianLinkId()).isEqualTo(l.getId().getStudentGuardianLinkId());
    }

    @Test
    @DisplayName("ACT-GUARDIAN_LINK_VIEW-DENY: a Student is refused: 403")
    void actGuardianLinkView_deny() {
        UUID student = t.staff("Student", "STUDENT_COLLEGE", ScopeType.INSTITUTION, college);
        TenantPrincipal p = t.as(student);
        assertThatThrownBy(() -> t.guardian.listGuardianLinks(t.tenantId, collegeStudent.getStudentProfileId(), p))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("ACT-GUARDIAN_LINK_VIEW-SCOPE: staff of another institution get 404; an unknown student is 404")
    void actGuardianLinkView_scope() {
        UUID adminOther = t.staff("Other Admin", "COLLEGE_ADMIN", ScopeType.INSTITUTION, otherCollege);
        TenantPrincipal p = t.as(adminOther);
        assertThatThrownBy(() -> t.guardian.listGuardianLinks(t.tenantId, collegeStudent.getStudentProfileId(), p))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> t.guardian.listGuardianLinks(t.tenantId, UUID.randomUUID(), p))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("ACT-GUARDIAN_CHANGE_APPROVE-GUARD: the requester approving gives 403; no pending request gives 422")
    void actGuardianChangeApprove_guard() {
        StudentGuardianLink l = t.link(collegeStudent.getStudentProfileId());
        TenantPrincipal p = t.as(principalCollege);

        // no pending request: 422
        assertThatThrownBy(() -> t.guardian.decideChange(t.tenantId, l.getId().getStudentGuardianLinkId(), p,
                new GuardianChangeDecisionRequest(true, "ok")))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("no pending change request");
        assertThat(l.getStatus()).isEqualTo("ACTIVE");

        // the Principal raised the request themselves and now approves it: 403
        ApprovalRequest own = t.guardianRequest(l, college, principalCollege, "PRINCIPAL_COLLEGE");
        l.setChangeApprovalRequestId(own.getId().getApprovalRequestId());
        assertThatThrownBy(() -> t.guardian.decideChange(t.tenantId, l.getId().getStudentGuardianLinkId(), p,
                new GuardianChangeDecisionRequest(true, "ok")))
                .isInstanceOf(SelfApprovalForbiddenException.class);
        assertThat(own.getStatus()).isEqualTo("PENDING");
        assertThat(l.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("ACT-GUARDIAN_CHANGE_APPROVE-ALLOW: the Principal approves a pending change: link blocked, step and audit saved")
    void actGuardianChangeApprove_allow() {
        StudentGuardianLink l = t.link(collegeStudent.getStudentProfileId());
        t.guardian.requestChange(t.tenantId, l.getId().getStudentGuardianLinkId(), t.as(adminCollege),
                new GuardianChangeRequestDto("Custody order"));

        t.guardian.decideChange(t.tenantId, l.getId().getStudentGuardianLinkId(), t.as(principalCollege),
                new GuardianChangeDecisionRequest(true, "verified"));

        assertThat(l.getStatus()).isEqualTo("BLOCKED");
        ApprovalRequest r = t.requests.values().iterator().next();
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(t.step(r, 1).getActedActiveRole()).isEqualTo("PRINCIPAL_COLLEGE");
        assertThat(t.audited("Guardian change decision for link")).isTrue();
    }

    @Test
    @DisplayName("REG-P02-1: a school request lands in the School Principal's inbox (and a second pending change is 409)")
    void reg_P02_1() {
        UUID schoolAdmin = t.staff("School Admin", "SCHOOL_ADMIN", ScopeType.INSTITUTION, school);
        UUID schoolPrincipal = t.staff("School Principal", "SCHOOL_PRINCIPAL", ScopeType.INSTITUTION, school);
        StudentProfile schoolStudent = t.student(school);
        StudentGuardianLink l = t.link(schoolStudent.getStudentProfileId());

        t.guardian.requestChange(t.tenantId, l.getId().getStudentGuardianLinkId(), t.as(schoolAdmin),
                new GuardianChangeRequestDto("Custody order"));

        ApprovalRequest r = t.requests.values().iterator().next();
        assertThat(t.step(r, 1).getApproverRoleCode()).isEqualTo("SCHOOL_PRINCIPAL");
        assertThat(r.getInstitutionId()).isEqualTo(school);
        assertThat(r.getRequesterActiveRole()).isEqualTo("SCHOOL_ADMIN");
        assertThat(t.inbox.listPendingApprovals(t.tenantId, t.as(schoolPrincipal)).items()).hasSize(1);
        TenantPrincipal again = t.as(schoolAdmin);
        assertThatThrownBy(() -> t.guardian.requestChange(t.tenantId, l.getId().getStudentGuardianLinkId(), again,
                new GuardianChangeRequestDto("Again")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("REG-P02-2: no institutionId parameter exists; the student's institution is always used")
    void reg_P02_2() {
        // Neither the controller nor the service takes an institution from the request.
        for (Method m : GuardianLinkController.class.getDeclaredMethods()) {
            assertThat(Arrays.stream(m.getParameters()).noneMatch(p -> p.isAnnotationPresent(RequestParam.class)))
                    .as("method %s has no request parameter", m.getName()).isTrue();
        }
        StudentGuardianLink l = t.link(collegeStudent.getStudentProfileId());
        t.guardian.requestChange(t.tenantId, l.getId().getStudentGuardianLinkId(), t.as(adminCollege),
                new GuardianChangeRequestDto("Custody order"));
        assertThat(t.requests.values().iterator().next().getInstitutionId()).isEqualTo(college);
    }

    // ------------------------------------------------------------------------------- P-03

    private UUID universityAdmin() {
        return t.staff("University Admin", "UNIVERSITY_ADMIN", ScopeType.TENANT, null);
    }

    private UUID viceChancellor() {
        return t.staff("Vice Chancellor", "VICE_CHANCELLOR", ScopeType.TENANT, null);
    }

    @Test
    @DisplayName("ACT-INSTITUTION_BLOCK_APPROVE-GUARD: no request gives 422; the requester approving gives 403; a second pending request gives 409")
    void actInstitutionBlockApprove_guard() {
        UUID ua = universityAdmin();
        UUID vc = viceChancellor();

        // no pending request: 422
        RbacPrincipal vcPrincipal = new RbacPrincipal(t.tenantId, vc);
        t.as(vc);
        assertThatThrownBy(() -> t.institutionService.decideBlock(vcPrincipal, college, new BlockDecisionRequest(true, "go")))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("no pending block request");
        assertThat(t.institutions.get(college).getStatus()).isEqualTo("ACTIVE");

        // a request, then a second one: 409
        t.as(ua);
        t.institutionService.requestBlock(new RbacPrincipal(t.tenantId, ua), college, new BlockInstitutionRequest("Audit failure"));
        assertThatThrownBy(() -> t.institutionService.requestBlock(new RbacPrincipal(t.tenantId, ua), college,
                new BlockInstitutionRequest("Again")))
                .isInstanceOf(DuplicateResourceException.class);
        assertThat(t.requests.values().stream().filter(r -> "INSTITUTION_BLOCK".equals(r.getProcessCode()))).hasSize(1);

        // the Vice Chancellor who raised a request approving it: 403
        UUID college2 = t.institution("COLLEGE", "ACTIVE");
        ApprovalRequest vcOwn = t.request("INSTITUTION_BLOCK", "INSTITUTION", college2, college2, vc, "VICE_CHANCELLOR");
        t.as(vc);
        assertThatThrownBy(() -> t.institutionService.decideBlock(vcPrincipal, college2, new BlockDecisionRequest(true, "self")))
                .isInstanceOf(SelfApprovalForbiddenException.class);
        assertThat(vcOwn.getStatus()).isEqualTo("PENDING");
        assertThat(t.institutions.get(college2).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("ACT-INSTITUTION_BLOCK_APPROVE-ALLOW: request then approval in the inbox blocks the institution; audit shows the real roles")
    void actInstitutionBlockApprove_allow() {
        UUID ua = universityAdmin();
        UUID vc = viceChancellor();
        t.as(ua);
        t.institutionService.requestBlock(new RbacPrincipal(t.tenantId, ua), college, new BlockInstitutionRequest("Audit failure"));

        // REG-P03-2: the requester role is the real active role
        ApprovalRequest r = t.requests.values().iterator().next();
        assertThat(r.getRequesterActiveRole()).isEqualTo("UNIVERSITY_ADMIN");
        assertThat(t.w.auditRows).anySatisfy(a -> {
            assertThat(a.getDetails()).contains("Block requested");
            assertThat(a.getActorRoleCode()).isEqualTo("UNIVERSITY_ADMIN");
        });
        assertThat(t.inbox.listPendingApprovals(t.tenantId, t.as(vc)).items()).hasSize(1);

        t.as(vc);
        var res = t.institutionService.decideBlock(new RbacPrincipal(t.tenantId, vc), college, new BlockDecisionRequest(true, "confirmed"));

        assertThat(res.status()).isEqualTo("BLOCKED");
        assertThat(t.institutions.get(college).getStatus()).isEqualTo("BLOCKED");
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(t.audited("blocked by approval request")).isTrue();
    }

    @Test
    @DisplayName("ACT-INSTITUTION_ACTIVATE-GUARD: activating a blocked institution directly gives 422; it needs its own request and approval")
    void actInstitutionActivate_guard() {
        UUID ua = universityAdmin();
        UUID vc = viceChancellor();
        t.institutions.get(college).setStatus("BLOCKED");
        t.leaveApproverInstitutions.add(college);

        t.as(vc);
        assertThatThrownBy(() -> t.institutionService.activateInstitution(new RbacPrincipal(t.tenantId, vc), college))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("own request and approval");
        assertThat(t.institutions.get(college).getStatus()).isEqualTo("BLOCKED");

        // the request, then the approval, reactivates it
        t.as(ua);
        t.institutionService.requestActivation(new RbacPrincipal(t.tenantId, ua), college);
        t.as(vc);
        var res = t.institutionService.activateInstitution(new RbacPrincipal(t.tenantId, vc), college);
        assertThat(res.status()).isEqualTo("ACTIVE");
        assertThat(t.institutions.get(college).getActivatedAt()).isNotNull();
        assertThat(t.audited("activated by approval request")).isTrue();
    }

    @Test
    @DisplayName("ACT-INSTITUTION_ACTIVATE-GUARD: the C7 go-live gate still refuses activation without a leave approver")
    void actInstitutionActivate_guard_c7() {
        UUID ua = universityAdmin();
        UUID vc = viceChancellor();
        UUID draft = t.institution("COLLEGE", "DRAFT");
        t.as(ua);
        t.institutionService.requestActivation(new RbacPrincipal(t.tenantId, ua), draft);
        t.as(vc);
        assertThatThrownBy(() -> t.institutionService.activateInstitution(new RbacPrincipal(t.tenantId, vc), draft))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("[C7]");
    }

    @Test
    @DisplayName("REG-P03-1: the old direct block path is gone (only /block-decisions remains)")
    void reg_P03_1() {
        for (Method m : InstitutionController.class.getDeclaredMethods()) {
            PostMapping pm = m.getAnnotation(PostMapping.class);
            if (pm != null) {
                assertThat(pm.value()).doesNotContain("/{institutionId}/block");
            }
        }
    }

    @Test
    @DisplayName("REG-P03-3: a block request without an active role is refused")
    void reg_P03_3() {
        UUID ua = universityAdmin();
        TenantContext.clear();
        assertThatThrownBy(() -> t.institutionService.requestBlock(new RbacPrincipal(t.tenantId, ua), college,
                new BlockInstitutionRequest("x")))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(t.requests).isEmpty();
    }

    // ------------------------------------------------------------------------------- P-04

    private PromotionService promotionService() {
        return new PromotionService(t.promotionRunRepository, t.w.evaluator, t.w.auditLogService,
                t.requestRepository, t.stepRepository, null);
    }

    @Test
    @DisplayName("ACT-PROMOTION_APPROVE-ALLOW: the School Principal approves through the inbox; run, step and audit saved")
    void actPromotionApprove_allow() {
        UUID registrar = t.staff("School Admin", "SCHOOL_ADMIN", ScopeType.INSTITUTION, school);
        UUID principal = t.staff("School Principal", "SCHOOL_PRINCIPAL", ScopeType.INSTITUTION, school);
        UUID runId = UUID.randomUUID();
        ApprovalRequest r = t.request("PROMOTION", "PROMOTION_RUN", runId, school, registrar, "SCHOOL_PRINCIPAL");
        PromotionRun run = ApprovalTestWorld.run(t.tenantId, school, "PENDING_APPROVAL", registrar, r.getId().getApprovalRequestId());
        t.runs.put(runId, run);
        run.getId();
        t.runs.clear();
        t.runs.put(run.getId().getPromotionRunId(), run);
        ApprovalRequest r2 = t.request("PROMOTION", "PROMOTION_RUN", run.getId().getPromotionRunId(), school, registrar, "SCHOOL_PRINCIPAL");

        assertThat(t.inbox.listPendingApprovals(t.tenantId, t.as(principal)).items()).hasSize(2);
        t.inbox.decideApproval(t.tenantId, r2.getId().getApprovalRequestId(),
                new DecideApprovalRequestDto((short) 1, "APPROVED", null, null), t.as(principal));

        assertThat(run.getStatus()).isEqualTo("APPROVED");
        assertThat(r2.getStatus()).isEqualTo("APPROVED");
        assertThat(t.step(r2, 1).getActedActiveRole()).isEqualTo("SCHOOL_PRINCIPAL");
        assertThat(t.audited("decided as APPROVED by SCHOOL_PRINCIPAL")).isTrue();
    }

    @Test
    @DisplayName("ACT-PROMOTION_APPROVE-GUARD: approving a DRAFT gives 422; the creator approving gives 403")
    void actPromotionApprove_guard() {
        UUID creator = t.staff("Principal creator", "SCHOOL_PRINCIPAL", ScopeType.INSTITUTION, school);
        UUID other = t.staff("Other School Principal", "SCHOOL_PRINCIPAL", ScopeType.INSTITUTION, school);
        PromotionRun draft = ApprovalTestWorld.run(t.tenantId, school, "DRAFT", creator, null);
        t.runs.put(draft.getId().getPromotionRunId(), draft);
        PromotionRun pending = ApprovalTestWorld.run(t.tenantId, school, "PENDING_APPROVAL", creator, null);
        t.runs.put(pending.getId().getPromotionRunId(), pending);

        TenantPrincipal p = t.as(other);
        assertThatThrownBy(() -> promotionService().decidePromotionRun(t.tenantId, p, draft.getId().getPromotionRunId(),
                new PromotionDecisionRequest("APPROVED", null)))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(draft.getStatus()).isEqualTo("DRAFT");

        TenantPrincipal c = t.as(creator);
        assertThatThrownBy(() -> promotionService().decidePromotionRun(t.tenantId, c, pending.getId().getPromotionRunId(),
                new PromotionDecisionRequest("APPROVED", null)))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(pending.getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("REG-P04-1: the promotion audit role equals the active role; with no active role the decision is denied")
    void reg_P04_1() {
        UUID creator = t.staff("School Admin", "SCHOOL_ADMIN", ScopeType.INSTITUTION, school);
        UUID principal = t.staff("School Principal", "SCHOOL_PRINCIPAL", ScopeType.INSTITUTION, school);
        UUID runId = UUID.randomUUID();
        ApprovalRequest r = t.request("PROMOTION", "PROMOTION_RUN", runId, school, creator, "SCHOOL_PRINCIPAL");
        PromotionRun run = ApprovalTestWorld.run(t.tenantId, school, "PENDING_APPROVAL", creator, r.getId().getApprovalRequestId());
        t.runs.put(run.getId().getPromotionRunId(), run);

        TenantContext.clear();
        TenantPrincipal none = new TenantPrincipal(t.tenantId, principal, com.edu360.api.identity.UserType.STAFF, "x", false);
        assertThatThrownBy(() -> promotionService().decidePromotionRun(t.tenantId, none, run.getId().getPromotionRunId(),
                new PromotionDecisionRequest("APPROVED", null)))
                .isInstanceOf(UnauthorizedException.class).hasMessageContaining("No active role");
        assertThat(run.getStatus()).isEqualTo("PENDING_APPROVAL");

        TenantPrincipal p = t.as(principal);
        promotionService().decidePromotionRun(t.tenantId, p, run.getId().getPromotionRunId(), new PromotionDecisionRequest("APPROVED", null));
        assertThat(run.getStatus()).isEqualTo("APPROVED");
        assertThat(t.step(r, 1).getActedActiveRole()).isEqualTo(TenantContext.currentActiveRoleCode()).isEqualTo("SCHOOL_PRINCIPAL");
    }

    @SuppressWarnings("unused")
    private Object unused() {
        return mock(Object.class);
    }
}
