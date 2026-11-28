package com.edu360.api.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.edu360.api.approval.dto.ApprovalInboxResponseDto;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.rbac.RbacTestWorld;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.TenantContext;

/**
 * Department-scoped approvers (HOD) in the shared approval engine. The evaluator, routing, inbox
 * and scope resolver are the real production classes; only storage is in memory.
 *
 * The HOD's assignment is DEPARTMENT scoped and the evaluator matches scopes exactly, so the
 * engine must check the HOD at the request's own department: the HOD of that department decides,
 * the HOD of another department never does, and institution-scoped approvers are unchanged.
 */
class ApprovalDepartmentScopeTest {

    private static final String CHECKIN_REVIEW = "STAFF_ATTENDANCE_CHECKIN_REVIEW";

    static {
        // sql/76_v41_staff_attendance_checkin.sql: an approval action, no sensitive class.
        // RbacTestWorld reads only 05/10d, so the V4.1 row is added here exactly as sql/76 defines it.
        RbacTestWorld.ACTIONS.putIfAbsent(CHECKIN_REVIEW, new RbacTestWorld.ActionRow(CHECKIN_REVIEW, true, null));
    }

    private ApprovalTestWorld t;
    private UUID inst;
    private UUID deptA;
    private UUID deptB;
    private UUID hodA;
    private UUID hodB;
    private UUID principal;
    private UUID facultyA;
    private final List<ApprovalRequest> applied = new ArrayList<>();

    @BeforeEach
    void setUp() {
        t = new ApprovalTestWorld();
        inst = t.institution("COLLEGE", "ACTIVE");
        deptA = UUID.randomUUID();
        deptB = UUID.randomUUID();

        hodA = t.w.user("HOD A");
        t.w.assign(hodA, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT, inst, deptA);
        t.staffAssignment(hodA, inst, deptA);
        hodB = t.w.user("HOD B");
        t.w.assign(hodB, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT, inst, deptB);
        t.staffAssignment(hodB, inst, deptB);
        principal = t.staff("Principal", "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION, inst);
        facultyA = t.staff("Faculty A", "ASSISTANT_PROFESSOR", ScopeType.INSTITUTION, inst);
        t.staffAssignment(facultyA, inst, deptA);

        // sql/76 grants: HOD (own department) and Principal (own institution).
        t.w.grant(t.w.standardRole("HEAD_OF_DEPARTMENT"), CHECKIN_REVIEW);
        t.w.grant(t.w.standardRole("PRINCIPAL_COLLEGE"), CHECKIN_REVIEW);

        t.inbox.setOutcomeHandlers(List.of(new ApprovalOutcomeHandler() {
            @Override public boolean supports(ApprovalRequest request) { return true; }
            @Override public void apply(UUID tenantId, ApprovalRequest request, boolean approved, UUID actor) {
                applied.add(request);
            }
        }));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ApprovalRequest checkin(UUID requester, String stepRole) {
        return t.request("STAFF_ATTENDANCE_REVIEW", "STAFF_ATTENDANCE_CHECKIN", UUID.randomUUID(), inst, requester, stepRole);
    }

    private ApprovalRequest facultyLeave(UUID requester) {
        return t.request("FACULTY_LEAVE_COLLEGE", "LEAVE_REQUEST", UUID.randomUUID(), inst, requester,
                "HEAD_OF_DEPARTMENT", "PRINCIPAL_COLLEGE");
    }

    private HttpStatus decide(UUID user, ApprovalRequest r, int stepNo, String decision, String remark) {
        try {
            TenantPrincipal p = t.as(user);
            t.inbox.decideApproval(t.tenantId, r.getId().getApprovalRequestId(),
                    new DecideApprovalRequestDto((short) stepNo, decision, remark, null), p);
            return HttpStatus.OK;
        } catch (RuntimeException e) {
            return e instanceof com.edu360.api.exception.ActiveRoleRequiredException ? HttpStatus.CONFLICT
                    : ApprovalTestWorld.statusOf(e);
        }
    }

    private List<UUID> inboxOf(UUID user) {
        TenantPrincipal p = t.as(user);
        ApprovalInboxResponseDto dto = t.inbox.listPendingApprovals(t.tenantId, p);
        List<UUID> ids = new ArrayList<>();
        dto.items().forEach(step -> t.steps.stream()
                .filter(s -> s.getStepNo() == step.stepNo() && s.getApproverRoleCode().equals(step.approverRoleCode()))
                .forEach(s -> ids.add(s.getApprovalRequestId())));
        return ids;
    }

    // ------------------------------------------------------------------ staff check-in review

    @Test
    @DisplayName("HOD of the check-in's department approves: 200, request APPROVED, outcome applied")
    void hodOfOwnDepartment_approvesCheckin() {
        ApprovalRequest r = checkin(facultyA, "HEAD_OF_DEPARTMENT");

        assertThat(decide(hodA, r, 1, "APPROVED", null)).isEqualTo(HttpStatus.OK);
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(t.step(r, 1).getActedByUserId()).isEqualTo(hodA);
        assertThat(t.step(r, 1).getActedActiveRole()).isEqualTo("HEAD_OF_DEPARTMENT");
        assertThat(applied).containsExactly(r);
    }

    @Test
    @DisplayName("HOD of the check-in's department rejects with a reason: 200, request REJECTED")
    void hodOfOwnDepartment_rejectsCheckin() {
        ApprovalRequest r = checkin(facultyA, "HEAD_OF_DEPARTMENT");

        assertThat(decide(hodA, r, 1, "REJECTED", "Photo does not show the staff member")).isEqualTo(HttpStatus.OK);
        assertThat(r.getStatus()).isEqualTo("REJECTED");
        assertThat(applied).containsExactly(r);
    }

    @Test
    @DisplayName("HOD of another department is refused on a check-in: 403, nothing changes")
    void hodOfOtherDepartment_isDeniedOnCheckin() {
        ApprovalRequest r = checkin(facultyA, "HEAD_OF_DEPARTMENT");

        assertThat(decide(hodB, r, 1, "APPROVED", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(t.step(r, 1).getDecision()).isEqualTo("PENDING");
        assertThat(applied).isEmpty();
    }

    @Test
    @DisplayName("Inbox: the department's HOD sees the check-in, another department's HOD does not")
    void inbox_showsCheckinOnlyToTheDepartmentsHod() {
        ApprovalRequest r = checkin(facultyA, "HEAD_OF_DEPARTMENT");
        UUID id = r.getId().getApprovalRequestId();

        assertThat(inboxOf(hodA)).contains(id);
        assertThat(inboxOf(hodB)).doesNotContain(id);
    }

    @Test
    @DisplayName("An HOD can never decide their own request (rule 11): 403")
    void hod_cannotApproveOwnRequest() {
        ApprovalRequest r = checkin(hodA, "HEAD_OF_DEPARTMENT");

        assertThat(decide(hodA, r, 1, "APPROVED", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Requester with no staff assignment: no department can be derived, the HOD is refused (fail closed)")
    void requesterWithoutDepartment_hodIsDenied() {
        UUID officeStaff = t.staff("Office", "ASSISTANT_PROFESSOR", ScopeType.INSTITUTION, inst);
        ApprovalRequest r = checkin(officeStaff, "HEAD_OF_DEPARTMENT");

        assertThat(decide(hodA, r, 1, "APPROVED", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Institution-scoped approver unchanged: the Principal decides a Principal check-in step")
    void principal_unchangedOnCheckin() {
        ApprovalRequest r = checkin(facultyA, "PRINCIPAL_COLLEGE");

        assertThat(decide(principal, r, 1, "APPROVED", null)).isEqualTo(HttpStatus.OK);
        assertThat(r.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("A delegate of the department's HOD decides with the HOD's department rights")
    void delegateOfHod_decidesForTheHodsDepartment() {
        UUID deputy = t.staff("Deputy", "PROFESSOR", ScopeType.INSTITUTION, inst);
        t.delegate(hodA, deputy, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS));
        ApprovalRequest r = checkin(facultyA, "HEAD_OF_DEPARTMENT");

        assertThat(decide(deputy, r, 1, "APPROVED", null)).isEqualTo(HttpStatus.OK);
        assertThat(t.step(r, 1).getActingViaDelegationId()).isNotNull();
    }

    @Test
    @DisplayName("Two ACTIVE assignments at the HOD's exact scope and no choice: never merged, the decision is refused")
    void ambiguousDualAssignment_isStillDenied() {
        UUID dual = t.w.user("Dual");
        t.w.assign(dual, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT, inst, deptA);
        var custom = t.w.customRole("DEPT_REVIEWER", "COLLEGE", inst, CHECKIN_REVIEW);
        t.w.assign(dual, custom, ScopeType.DEPARTMENT, inst, deptA);
        ApprovalRequest r = checkin(facultyA, "HEAD_OF_DEPARTMENT");

        assertThat(decide(dual, r, 1, "APPROVED", null)).isNotEqualTo(HttpStatus.OK);
        assertThat(r.getStatus()).isEqualTo("PENDING");
    }

    // ------------------------------------------------------------------ staff leave (HOD recommends)

    @Test
    @DisplayName("Faculty leave: the department's HOD recommends (step 1), then the Principal approves (step 2)")
    void facultyLeave_hodThenPrincipal() {
        ApprovalRequest r = facultyLeave(facultyA);

        assertThat(decide(hodA, r, 1, "APPROVED", null)).isEqualTo(HttpStatus.OK);
        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getCurrentStepNo()).isEqualTo((short) 2);
        assertThat(t.step(r, 1).getDecision()).isEqualTo("APPROVED");

        assertThat(decide(principal, r, 2, "APPROVED", null)).isEqualTo(HttpStatus.OK);
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(applied).containsExactly(r);
    }

    @Test
    @DisplayName("Faculty leave: the department's HOD may reject with a remark")
    void facultyLeave_hodRejects() {
        ApprovalRequest r = facultyLeave(facultyA);

        assertThat(decide(hodA, r, 1, "REJECTED", "Exam week")).isEqualTo(HttpStatus.OK);
        assertThat(r.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("Faculty leave: another department's HOD is refused, and does not see it in the inbox")
    void facultyLeave_otherDepartmentHodDenied() {
        ApprovalRequest r = facultyLeave(facultyA);

        assertThat(inboxOf(hodB)).doesNotContain(r.getId().getApprovalRequestId());
        assertThat(inboxOf(hodA)).contains(r.getId().getApprovalRequestId());
        assertThat(decide(hodB, r, 1, "APPROVED", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getCurrentStepNo()).isEqualTo((short) 1);
        assertThat(t.step(r, 1).getDecision()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Faculty leave: the HOD step is not open to the Principal by scope alone (routing still applies)")
    void facultyLeave_principalCannotTakeTheHodStep() {
        ApprovalRequest r = facultyLeave(facultyA);

        assertThat(decide(principal, r, 1, "APPROVED", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(t.step(r, 1).getDecision()).isEqualTo("PENDING");
    }
}
