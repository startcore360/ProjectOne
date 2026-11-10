package com.edu360.api.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.edu360.api.approval.dto.ApprovalInboxResponseDto;
import com.edu360.api.approval.dto.BulkApprovalRequest;
import com.edu360.api.approval.dto.BulkApprovalResponse;
import com.edu360.api.approval.dto.CreateDelegationRequest;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.GlobalExceptionHandler;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.SelfApprovalForbiddenException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.guardian.StudentGuardianLink;
import com.edu360.api.identity.StudentProfile;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rolldown.CreateRolldownOverrideRequest;
import com.edu360.api.tenant.TenantContext;

import jakarta.servlet.http.HttpServletRequest;

/**
 * B2 / P-01, P-05, P-06, P-07, P-08, P-09: the approval engine. The evaluator, routing, inbox and
 * delegation services are the real production classes (R3); only the repositories are in memory.
 */
class ApprovalEngineB2Test {

    private ApprovalTestWorld t;
    private UUID instA;
    private UUID instB;
    private UUID principalA;     // PRINCIPAL_COLLEGE at A
    private UUID adminA;         // COLLEGE_ADMIN at A, the requester
    private StudentGuardianLink link;
    private ApprovalRequest req; // GUARDIAN_CHANGE at A, step 1 = PRINCIPAL_COLLEGE

    @BeforeEach
    void setUp() {
        t = new ApprovalTestWorld();
        instA = t.institution("COLLEGE", "ACTIVE");
        instB = t.institution("COLLEGE", "ACTIVE");
        principalA = t.staff("Principal A", "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, instA);
        adminA = t.staff("Admin A", "COLLEGE_ADMIN", ScopeType.INSTITUTION, instA);
        StudentProfile s = t.student(instA);
        link = t.link(s.getStudentProfileId());
        req = t.guardianRequest(link, instA, adminA, "PRINCIPAL_COLLEGE");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private DecideApprovalRequestDto approve() {
        return new DecideApprovalRequestDto((short) 1, "APPROVED", null, null);
    }

    private void decide(UUID user, ApprovalRequest r) {
        TenantPrincipal p = t.as(user);
        t.inbox.decideApproval(t.tenantId, r.getId().getApprovalRequestId(), approve(), p);
    }

    private HttpStatus decideStatus(UUID user, ApprovalRequest r) {
        try {
            decide(user, r);
            return HttpStatus.OK;
        } catch (RuntimeException e) {
            return ApprovalTestWorld.statusOf(e);
        }
    }

    // ------------------------------------------------------------------------------- P-01

    @Test
    @DisplayName("REG-P01-1: the step's role holder in scope decides: 200, state and audit saved")
    void reg_P01_1() {
        decide(principalA, req);

        assertThat(req.getStatus()).isEqualTo("APPROVED");
        ApprovalStep step = t.step(req, 1);
        assertThat(step.getDecision()).isEqualTo("APPROVED");
        assertThat(step.getActedByUserId()).isEqualTo(principalA);
        assertThat(step.getActedActiveRole()).isEqualTo("PRINCIPAL_COLLEGE");
        assertThat(link.getStatus()).isEqualTo("BLOCKED");
        assertThat(t.audited("decided as APPROVED by PRINCIPAL_COLLEGE")).isTrue();
        assertThat(t.w.auditRows).anySatisfy(a -> assertThat(a.getActorRoleCode()).isEqualTo("PRINCIPAL_COLLEGE"));
    }

    @Test
    @DisplayName("REG-P01-2: a holder of the action with a different role is refused: 403, nothing changes")
    void reg_P01_2() {
        var deputyRole = t.w.customRole("DEPUTY_PRINCIPAL", "COLLEGE", instA, "GUARDIAN_CHANGE_APPROVE");
        UUID deputy = t.w.user("Deputy");
        t.w.assign(deputy, deputyRole, ScopeType.INSTITUTION, instA, null);

        assertThat(decideStatus(deputy, req)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(req.getStatus()).isEqualTo("PENDING");
        assertThat(t.step(req, 1).getDecision()).isEqualTo("PENDING");
        assertThat(link.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("REG-P01-3: a delegate for that role within dates decides: 200 and the audit shows acting for")
    void reg_P01_3() {
        UUID vp = t.staff("Vice Principal", "VICE_PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, instA);
        Delegation d = t.delegate(principalA, vp, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));

        decide(vp, req);

        assertThat(req.getStatus()).isEqualTo("APPROVED");
        assertThat(t.step(req, 1).getActedByUserId()).isEqualTo(vp);
        assertThat(t.step(req, 1).getActingViaDelegationId()).isEqualTo(d.getId().getDelegationId());
        assertThat(t.audited("acting for PRINCIPAL_COLLEGE by delegation " + d.getId().getDelegationId())).isTrue();
    }

    @Test
    @DisplayName("REG-P01-4: an expired delegate is refused: 403")
    void reg_P01_4() {
        UUID vp = t.staff("Vice Principal", "VICE_PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, instA);
        t.delegate(principalA, vp, Instant.now().minus(3, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(decideStatus(vp, req)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(req.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("REG-P01-5: the requester is refused: 403 (and the handler answers 403)")
    void reg_P01_5() {
        ApprovalRequest own = t.guardianRequest(link, instA, principalA, "PRINCIPAL_COLLEGE");

        assertThatThrownBy(() -> decide(principalA, own)).isInstanceOf(SelfApprovalForbiddenException.class);
        assertThat(own.getStatus()).isEqualTo("PENDING");
        var response = new GlobalExceptionHandler().handleSelfApproval(new SelfApprovalForbiddenException("x"), mock(HttpServletRequest.class));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("REG-P01-6: the same person on step 2 after step 1 is refused: 403")
    void reg_P01_6() {
        ApprovalRequest two = t.guardianRequest(link, instA, adminA, "PRINCIPAL_COLLEGE", "PRINCIPAL_COLLEGE");
        decide(principalA, two);
        assertThat(two.getCurrentStepNo()).isEqualTo((short) 2);

        TenantPrincipal p = t.as(principalA);
        assertThatThrownBy(() -> t.inbox.decideApproval(t.tenantId, two.getId().getApprovalRequestId(),
                new DecideApprovalRequestDto((short) 2, "APPROVED", null, null), p))
                .isInstanceOf(UnauthorizedException.class).hasMessageContaining("Separation of duties");
        assertThat(t.step(two, 2).getDecision()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("REG-P01-7: an unknown process is refused: 403, never allowed by fallback")
    void reg_P01_7() {
        ApprovalRequest unknown = t.request("MYSTERY_PROCESS", "MYSTERY", UUID.randomUUID(), instA, adminA, "PRINCIPAL_COLLEGE");

        assertThat(decideStatus(principalA, unknown)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unknown.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("REG-P01-8: the role holder of institution A deciding institution B's request: 404")
    void reg_P01_8() {
        StudentProfile sb = t.student(instB);
        ApprovalRequest other = t.guardianRequest(t.link(sb.getStudentProfileId()), instB, adminA, "PRINCIPAL_COLLEGE");

        assertThat(decideStatus(principalA, other)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(other.getStatus()).isEqualTo("PENDING");
    }

    // ------------------------------------------------------------------------------- P-05

    private int inboxSize(UUID user) {
        ApprovalInboxResponseDto r = t.inbox.listPendingApprovals(t.tenantId, t.as(user));
        return r.items().size();
    }

    @Test
    @DisplayName("REG-P05-1: the Principal of A sees only A's steps")
    void reg_P05_1() {
        StudentProfile sb = t.student(instB);
        t.guardianRequest(t.link(sb.getStudentProfileId()), instB, adminA, "PRINCIPAL_COLLEGE");

        assertThat(inboxSize(principalA)).isEqualTo(1);
    }

    @Test
    @DisplayName("REG-P05-2: the University Admin (tenant-wide) sees every institution")
    void reg_P05_2() {
        UUID ua = t.staff("University Admin", "UNIVERSITY_ADMIN", ScopeType.TENANT, null);
        UUID subject = UUID.randomUUID();
        t.request("INSTITUTION_BLOCK", "INSTITUTION", subject, instA, adminA, "UNIVERSITY_ADMIN");
        t.request("INSTITUTION_BLOCK", "INSTITUTION", UUID.randomUUID(), instB, adminA, "UNIVERSITY_ADMIN");

        assertThat(inboxSize(ua)).isEqualTo(2);
    }

    @Test
    @DisplayName("REG-P05-3: no tenant context shows an empty list")
    void reg_P05_3() {
        TenantContext.clear();

        ApprovalInboxResponseDto r = t.inbox.listPendingApprovals(t.tenantId,
                new TenantPrincipal(t.tenantId, principalA, com.edu360.api.identity.UserType.STAFF, "x", false));

        assertThat(r.items()).isEmpty();
    }

    // ------------------------------------------------------------------------------- P-08

    private HttpStatus readStatus(UUID user, UUID tenant, UUID requestId) {
        TenantPrincipal p = t.as(user);
        try {
            t.inbox.getApprovalRequest(tenant, requestId, p);
            return HttpStatus.OK;
        } catch (ResourceNotFoundException e) {
            return HttpStatus.NOT_FOUND;
        }
    }

    @Test
    @DisplayName("REG-P08-1: the requester reads the request: 200")
    void reg_P08_1() {
        assertThat(readStatus(adminA, t.tenantId, req.getId().getApprovalRequestId())).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("REG-P08-2: an approver of a step reads the request: 200")
    void reg_P08_2() {
        assertThat(readStatus(principalA, t.tenantId, req.getId().getApprovalRequestId())).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("REG-P08-3: an unrelated user reads the request: 404")
    void reg_P08_3() {
        UUID teacher = t.staff("Teacher", "SUBJECT_TEACHER", ScopeType.INSTITUTION, instA);
        assertThat(readStatus(teacher, t.tenantId, req.getId().getApprovalRequestId())).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("REG-P08-4: another tenant's request ID reads as 404")
    void reg_P08_4() {
        assertThat(readStatus(principalA, UUID.randomUUID(), req.getId().getApprovalRequestId())).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readStatus(principalA, t.tenantId, UUID.randomUUID())).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------------------- P-09

    @Test
    @DisplayName("REG-P09-1 (E2E-035): no holder routes to the stand-in, who decides, and the audit shows acting for")
    void reg_P09_1() {
        t.requests.remove(req.getId().getApprovalRequestId());
        t.steps.removeIf(s -> s.getApprovalRequestId().equals(req.getId().getApprovalRequestId()));
        // The Data Protection Contact's approvals roll down to the college Principal (RL-08); nobody holds the role.
        ApprovalRequest r = t.guardianRequest(link, instA, adminA, "DATA_PROTECTION_CONTACT");

        assertThat(inboxSize(principalA)).isEqualTo(1);
        decide(principalA, r);

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(t.step(r, 1).getActedByUserId()).isEqualTo(principalA);
        assertThat(t.audited("by PRINCIPAL_COLLEGE (acting for DATA_PROTECTION_CONTACT by roll-down")).isTrue();
    }

    @Test
    @DisplayName("REG-P09-1b: when the role has a holder, the stand-in cannot act")
    void reg_P09_1b() {
        t.requests.remove(req.getId().getApprovalRequestId());
        t.steps.removeIf(s -> s.getApprovalRequestId().equals(req.getId().getApprovalRequestId()));
        t.staff("DPC", "DATA_PROTECTION_CONTACT", ScopeType.INSTITUTION, instA);
        ApprovalRequest r = t.guardianRequest(link, instA, adminA, "DATA_PROTECTION_CONTACT");

        assertThat(inboxSize(principalA)).isZero();
        assertThat(decideStatus(principalA, r)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("REG-P09-2 (E2E-036): Management changes the stand-in and the new one sees the item")
    void reg_P09_2() {
        t.requests.remove(req.getId().getApprovalRequestId());
        t.steps.removeIf(s -> s.getApprovalRequestId().equals(req.getId().getApprovalRequestId()));
        var management = t.w.standardRole("MANAGEMENT");
        t.w.grant(management, "ROLLDOWN_OVERRIDE_SET");
        UUID mgr = t.w.user("Management");
        t.w.assign(mgr, management, ScopeType.TENANT, null, null);
        var deputyRole = t.w.customRole("DEPUTY_PRINCIPAL", "COLLEGE", instA, "GUARDIAN_CHANGE_APPROVE");
        UUID deputy = t.w.user("Deputy");
        t.w.assign(deputy, deputyRole, ScopeType.INSTITUTION, instA, null);
        ApprovalRequest r = t.guardianRequest(link, instA, adminA, "DATA_PROTECTION_CONTACT");
        assertThat(inboxSize(principalA)).isEqualTo(1);
        assertThat(inboxSize(deputy)).isZero();

        TenantPrincipal m = t.as(mgr);
        t.rolldown.createOverride(t.tenantId, m, null, new CreateRolldownOverrideRequest(instA,
                "DATA_PROTECTION_CONTACT", "APPROVE", null, "ASSIGN", "DEPUTY_PRINCIPAL", "Deputy covers the contact"));

        assertThat(inboxSize(deputy)).isEqualTo(1);
        assertThat(inboxSize(principalA)).isZero();
        decide(deputy, r);
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(t.audited("by DEPUTY_PRINCIPAL (acting for DATA_PROTECTION_CONTACT by roll-down")).isTrue();
    }

    // ------------------------------------------------------------------------------- P-06

    private ApprovalRequest guardianRequestAtA() {
        StudentProfile s = t.student(instA);
        return t.guardianRequest(t.link(s.getStudentProfileId()), instA, adminA, "PRINCIPAL_COLLEGE");
    }

    @Test
    @DisplayName("ACT-APPROVAL_BULK-ALLOW: 3 valid items are all decided and their states change")
    void actApprovalBulk_allow() {
        List<ApprovalRequest> three = List.of(guardianRequestAtA(), guardianRequestAtA(), guardianRequestAtA());
        TenantPrincipal p = t.as(principalA);

        BulkApprovalResponse res = t.delegation.bulkDecide(t.tenantId, p, new BulkApprovalRequest(
                three.stream().map(r -> r.getId().getApprovalRequestId()).toList(), "APPROVED", "ok"));

        assertThat(res.succeeded()).isEqualTo(3);
        assertThat(res.results()).extracting(BulkApprovalResponse.ItemResult::outcome).containsOnly("APPROVED");
        assertThat(three).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo("APPROVED"));
        assertThat(t.w.auditRows.stream().filter(a -> a.getDetails() != null && a.getDetails().contains("Bulk batch")).count()).isEqualTo(3);
    }

    @Test
    @DisplayName("ACT-APPROVAL_BULK-GUARD: a DPO item gives 422 and nothing is decided; another tenant's item gives 404")
    void actApprovalBulk_guard() {
        ApprovalRequest ok = guardianRequestAtA();
        ApprovalRequest dpo = t.request("TENANT_EXPORT", "DATA_PROTECTION", UUID.randomUUID(), instA, adminA, "PRINCIPAL_COLLEGE");
        TenantPrincipal p = t.as(principalA);

        assertThatThrownBy(() -> t.delegation.bulkDecide(t.tenantId, p, new BulkApprovalRequest(
                List.of(ok.getId().getApprovalRequestId(), dpo.getId().getApprovalRequestId()), "APPROVED", null)))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("never bulk-approved");
        assertThat(ok.getStatus()).isEqualTo("PENDING");
        assertThat(dpo.getStatus()).isEqualTo("PENDING");

        assertThatThrownBy(() -> t.delegation.bulkDecide(t.tenantId, p, new BulkApprovalRequest(
                List.of(ok.getId().getApprovalRequestId(), UUID.randomUUID()), "APPROVED", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(ok.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("REG-P06-1: D5 bulkApprove reports per-item outcomes; one failing item does not undo the others")
    void reg_P06_1() {
        ApprovalRequest good1 = guardianRequestAtA();
        ApprovalRequest good2 = guardianRequestAtA();
        ApprovalRequest done = guardianRequestAtA();
        done.setStatus("APPROVED");
        StudentProfile s = t.student(instA);
        ApprovalRequest wrongRole = t.guardianRequest(t.link(s.getStudentProfileId()), instA, adminA, "VICE_CHANCELLOR");
        TenantPrincipal p = t.as(principalA);

        BulkApprovalResponse res = t.delegation.bulkDecide(t.tenantId, p, new BulkApprovalRequest(
                List.of(good1, done, wrongRole, good2).stream().map(r -> r.getId().getApprovalRequestId()).toList(), "APPROVED", null));

        assertThat(res.results()).extracting(BulkApprovalResponse.ItemResult::outcome)
                .containsExactly("APPROVED", "ALREADY_ACTIONED", "NOT_CURRENT_APPROVER", "APPROVED");
        assertThat(good1.getStatus()).isEqualTo("APPROVED");
        assertThat(good2.getStatus()).isEqualTo("APPROVED");
        assertThat(wrongRole.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("REG-P06-2: a rejection is never a bulk decision")
    void reg_P06_2() {
        ApprovalRequest r = guardianRequestAtA();
        TenantPrincipal p = t.as(principalA);
        assertThatThrownBy(() -> t.delegation.bulkDecide(t.tenantId, p,
                new BulkApprovalRequest(List.of(r.getId().getApprovalRequestId()), "REJECTED", "no")))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(r.getStatus()).isEqualTo("PENDING");
    }

    // ------------------------------------------------------------------------------- P-07

    private CreateDelegationRequest delegationTo(UUID delegate, String process) {
        return new CreateDelegationRequest(delegate, "LEAVE", process,
                Instant.now().minusSeconds(60), Instant.now().plus(2, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("ACT-APPROVAL_DELEGATE-ALLOW: the Principal delegates to the Vice Principal; state and audit saved")
    void actApprovalDelegate_allow() {
        UUID vp = t.staff("Vice Principal", "VICE_PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, instA);
        TenantPrincipal p = t.as(principalA);

        var res = t.delegation.createDelegation(t.tenantId, p, delegationTo(vp, "GUARDIAN_CHANGE_APPROVE"));

        assertThat(t.delegations).hasSize(1);
        assertThat(t.delegations.get(0).getDelegatorUserId()).isEqualTo(principalA);
        assertThat(t.delegations.get(0).getDelegateUserId()).isEqualTo(vp);
        assertThat(t.delegations.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(t.audited("Created delegation " + res.delegationId())).isTrue();
    }

    @Test
    @DisplayName("ACT-APPROVAL_DELEGATE-DENY: a Teacher is refused: 403, nothing saved")
    void actApprovalDelegate_deny() {
        UUID teacher = t.staff("Teacher", "SUBJECT_TEACHER", ScopeType.INSTITUTION, instA);
        TenantPrincipal p = t.as(teacher);
        assertThatThrownBy(() -> t.delegation.createDelegation(t.tenantId, p, delegationTo(principalA, null)))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(t.delegations).isEmpty();
    }

    @Test
    @DisplayName("ACT-APPROVAL_DELEGATE-SCOPE: delegating to a user of another institution gives 422")
    void actApprovalDelegate_scope() {
        UUID vpB = t.staff("Vice Principal B", "VICE_PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, instB);
        TenantPrincipal p = t.as(principalA);
        assertThatThrownBy(() -> t.delegation.createDelegation(t.tenantId, p, delegationTo(vpB, null)))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("does not work in the delegator's institution");
        assertThat(t.delegations).isEmpty();
    }

    @Test
    @DisplayName("ACT-APPROVAL_DELEGATE-GUARD: inactive delegate, re-delegation, merit list, other canceller, own request")
    void actApprovalDelegate_guard() {
        UUID vp = t.staff("Vice Principal", "VICE_PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, instA);
        TenantPrincipal p = t.as(principalA);

        // an inactive delegate: 422
        t.w.accounts.get(vp).setStatus(UserAccountStatus.DEACTIVATED);
        assertThatThrownBy(() -> t.delegation.createDelegation(t.tenantId, p, delegationTo(vp, null)))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("not active");
        t.w.accounts.get(vp).setStatus(UserAccountStatus.ACTIVE);

        // MERIT_LIST_APPROVE: 422
        assertThatThrownBy(() -> t.delegation.createDelegation(t.tenantId, p, delegationTo(vp, "MERIT_LIST_APPROVE")))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("never be delegated");

        // re-delegation: a Principal acting by a delegation cannot hand it on: 422
        UUID principalA2 = t.staff("Principal A2", "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, instA);
        t.delegate(principalA, principalA2, Instant.now().minusSeconds(60), Instant.now().plus(1, ChronoUnit.DAYS));
        TenantPrincipal p2 = t.as(principalA2);
        assertThatThrownBy(() -> t.delegation.createDelegation(t.tenantId, p2, delegationTo(vp, null)))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("cannot delegate the delegated");

        // another user cancelling: 403
        Delegation d = t.delegate(principalA, vp, Instant.now().minusSeconds(60), Instant.now().plus(1, ChronoUnit.DAYS));
        UUID teacher = t.staff("Teacher", "SUBJECT_TEACHER", ScopeType.INSTITUTION, instA);
        TenantPrincipal tp = t.as(teacher);
        assertThatThrownBy(() -> t.delegation.cancelDelegation(t.tenantId, d.getId().getDelegationId(), tp))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(d.getStatus()).isEqualTo("ACTIVE");

        // the delegate deciding their own request: 403
        ApprovalRequest own = t.guardianRequest(link, instA, vp, "PRINCIPAL_COLLEGE");
        assertThat(decideStatus(vp, own)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(own.getStatus()).isEqualTo("PENDING");

        // the delegator can cancel: state saved
        TenantPrincipal again = t.as(principalA);
        t.delegation.cancelDelegation(t.tenantId, d.getId().getDelegationId(), again);
        assertThat(d.getStatus()).isEqualTo("CANCELLED");
        assertThat(t.audited("Cancelled delegation " + d.getId().getDelegationId())).isTrue();
    }

    @Test
    @DisplayName("REG-P07-1: after the end date, the items show in the delegator's inbox again")
    void reg_P07_1() {
        UUID vp = t.staff("Vice Principal", "VICE_PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, instA);
        Delegation d = t.delegate(principalA, vp, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));

        // while it runs the item sits with the delegate
        assertThat(inboxSize(vp)).isEqualTo(1);
        assertThat(inboxSize(principalA)).isZero();

        // the end date passes
        d.setEndsAt(Instant.now().minusSeconds(1));
        assertThat(inboxSize(principalA)).isEqualTo(1);
        assertThat(inboxSize(vp)).isZero();
    }
}
