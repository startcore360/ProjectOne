package com.edu360.api.selfservice;

import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.ApprovalRequestId;
import com.edu360.api.approval.ApprovalRequestRepository;
import com.edu360.api.approval.ApprovalStep;
import com.edu360.api.approval.ApprovalStepId;
import com.edu360.api.approval.ApprovalStepRepository;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.LeaveApproverSetting;
import com.edu360.api.institution.LeaveApproverSettingRepository;
import com.edu360.api.institution.StaffAssignment;
import com.edu360.api.institution.StaffAssignmentRepository;
import com.edu360.api.institution.StaffProfile;
import com.edu360.api.institution.StaffProfileRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.selfservice.dto.LeaveBalanceCreditRequest;
import com.edu360.api.selfservice.dto.LeaveRequestCreateRequest;
import com.edu360.api.selfservice.dto.LeaveRequestResponse;
import com.edu360.api.selfservice.dto.StaffLeaveBalanceResponse;
import com.edu360.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffLeaveService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(1);
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final StaffProfileRepository staffProfiles;
    private final StaffAssignmentRepository staffAssignments;
    private final InstitutionRepository institutions;
    private final LeaveApproverSettingRepository approverSettings;
    private final ApprovalRequestRepository approvalRequests;
    private final ApprovalStepRepository approvalSteps;

    public StaffLeaveService(JdbcTemplate jdbc, PermissionEvaluatorService permissions,
            StaffProfileRepository staffProfiles, StaffAssignmentRepository staffAssignments,
            InstitutionRepository institutions, LeaveApproverSettingRepository approverSettings,
            ApprovalRequestRepository approvalRequests, ApprovalStepRepository approvalSteps) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.staffProfiles = staffProfiles;
        this.staffAssignments = staffAssignments;
        this.institutions = institutions;
        this.approverSettings = approverSettings;
        this.approvalRequests = approvalRequests;
        this.approvalSteps = approvalSteps;
    }

    @Transactional(readOnly = true)
    public List<StaffLeaveBalanceResponse> ownBalances(UUID tenantId, UUID userId) {
        StaffProfile staff = staffProfiles.findByIdTenantIdAndUserAccountId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff profile not found"));
        return balances(tenantId, staff.getStaffProfileId());
    }

    @Transactional(readOnly = true)
    public List<StaffLeaveBalanceResponse> balancesForActor(UUID tenantId, UUID actorId, UUID staffProfileId) {
        StaffProfile target = staffProfiles.findByIdTenantIdAndIdStaffProfileId(tenantId, staffProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff profile not found"));
        if (!actorId.equals(target.getUserAccountId())) {
            UUID institutionId = activeInstitution(tenantId, staffProfileId);
            RbacPrincipal actor = new RbacPrincipal(tenantId, actorId);
            permissions.authorize(actor, "STAFF_RECORD_VIEW",
                    institutionId == null ? RbacScope.tenant() : permissions.targetScope(actor, institutionId, null),
                    target.getUserAccountId() == null ? RbacTarget.NONE : RbacTarget.of(target.getUserAccountId()));
        }
        return balances(tenantId, staffProfileId);
    }

    @Transactional(readOnly = true)
    public List<StaffLeaveBalanceResponse> balances(UUID tenantId, UUID staffProfileId) {
        return jdbc.query("""
                SELECT leave_balance_id, staff_profile_id, leave_type_id, leave_year,
                       credited_days, used_days, credited_days-used_days AS available_days
                  FROM app.leave_balance
                 WHERE tenant_id=? AND staff_profile_id=?
                 ORDER BY leave_year DESC, leave_type_id
                """, (rs, row) -> new StaffLeaveBalanceResponse(
                    rs.getObject("leave_balance_id", UUID.class),
                    rs.getObject("staff_profile_id", UUID.class),
                    rs.getObject("leave_type_id", UUID.class),
                    rs.getShort("leave_year"),
                    rs.getBigDecimal("credited_days"),
                    rs.getBigDecimal("used_days"),
                    rs.getBigDecimal("available_days")), tenantId, staffProfileId);
    }

    @Transactional
    public StaffLeaveBalanceResponse creditBalance(UUID tenantId, UUID actorId, UUID staffProfileId,
            LeaveBalanceCreditRequest request) {
        if (request == null || request.leaveTypeId() == null || request.days() == null
                || request.days().signum() < 0 || request.days().multiply(BigDecimal.valueOf(2))
                    .stripTrailingZeros().scale() > 0 || request.leaveYear() < 2000 || request.leaveYear() > 2200) {
            throw new BusinessRuleViolationException("INVALID_LEAVE_CREDIT", "Leave year and non-negative half-day increments are required.");
        }
        StaffProfile staff = staffProfiles.findByIdTenantIdAndIdStaffProfileId(tenantId, staffProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff profile not found"));
        UUID institutionId = activeInstitution(tenantId, staffProfileId);
        RbacPrincipal actor = new RbacPrincipal(tenantId, actorId);
        permissions.authorize(actor, "LEAVE_BALANCE_CREDIT",
                institutionId == null ? RbacScope.tenant() : permissions.targetScope(actor, institutionId, null),
                staff.getUserAccountId() == null ? RbacTarget.NONE : RbacTarget.of(staff.getUserAccountId()));
        Map<String, Object> type = leaveType(tenantId, request.leaveTypeId());
        if (!"STAFF".equals(type.get("audience")) || !Boolean.TRUE.equals(type.get("tracks_balance"))
                || Boolean.TRUE.equals(type.get("is_loss_of_pay")) || !"ACTIVE".equals(type.get("status"))) {
            throw new BusinessRuleViolationException("LEAVE_TYPE_NOT_CREDITABLE", "Only active balance-tracked staff leave types can be credited.");
        }
        if (institutionId == null || !institutionId.equals(type.get("institution_id"))) {
            throw new BusinessRuleViolationException("LEAVE_TYPE_SCOPE_MISMATCH", "Leave type does not belong to the staff member's institution.");
        }
        jdbc.update("""
                INSERT INTO app.leave_balance
                  (tenant_id,leave_balance_id,institution_id,staff_profile_id,leave_type_id,leave_year,
                   credited_days,used_days,credited_by,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?,0,?,?,?)
                ON CONFLICT (tenant_id,staff_profile_id,leave_type_id,leave_year)
                DO UPDATE SET credited_days=app.leave_balance.credited_days+EXCLUDED.credited_days,
                              credited_by=EXCLUDED.credited_by,updated_by=EXCLUDED.updated_by
                """, tenantId, UUID.randomUUID(), institutionId, staffProfileId, request.leaveTypeId(),
                request.leaveYear(), request.days(), actorId, actorId, actorId);
        return jdbc.queryForObject("""
                SELECT leave_balance_id,staff_profile_id,leave_type_id,leave_year,credited_days,used_days,
                       credited_days-used_days AS available_days
                  FROM app.leave_balance WHERE tenant_id=? AND staff_profile_id=? AND leave_type_id=? AND leave_year=?
                """, (rs, row) -> new StaffLeaveBalanceResponse(
                    rs.getObject("leave_balance_id", UUID.class), rs.getObject("staff_profile_id", UUID.class),
                    rs.getObject("leave_type_id", UUID.class), rs.getShort("leave_year"),
                    rs.getBigDecimal("credited_days"), rs.getBigDecimal("used_days"),
                    rs.getBigDecimal("available_days")), tenantId, staffProfileId, request.leaveTypeId(), request.leaveYear());
    }

    @Transactional
    public LeaveRequestResponse submit(UUID tenantId, TenantPrincipal principal, LeaveRequestCreateRequest request) {
        if (request == null || request.leaveTypeId() == null || request.startDate() == null
                || request.endDate() == null || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 1000) {
            throw new BusinessRuleViolationException("INVALID_LEAVE_REQUEST", "Leave type, dates and a reason of 1-1000 characters are required.");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleViolationException("INVALID_LEAVE_DATES", "End date cannot be before start date.");
        }
        if (request.startDate().getYear() != request.endDate().getYear()) {
            throw new BusinessRuleViolationException("CROSS_YEAR_LEAVE_UNSUPPORTED", "A leave request must fall within one calendar leave year.");
        }
        String unit = request.unit() == null ? "FULL_DAY" : request.unit().toUpperCase();
        if (!List.of("FULL_DAY", "HALF_DAY").contains(unit)) {
            throw new BusinessRuleViolationException("INVALID_LEAVE_UNIT", "Staff leave supports FULL_DAY or HALF_DAY units.");
        }
        if ("HALF_DAY".equals(unit) && !List.of("FIRST_HALF", "SECOND_HALF").contains(request.halfDayPart())) {
            throw new BusinessRuleViolationException("INVALID_HALF_DAY_PART", "Choose FIRST_HALF or SECOND_HALF for a half-day request.");
        }
        if ("FULL_DAY".equals(unit) && request.halfDayPart() != null) {
            throw new BusinessRuleViolationException("INVALID_HALF_DAY_PART", "halfDayPart is only valid for HALF_DAY requests.");
        }
        UUID userId = principal.userAccountId();
        RbacPrincipal actor = new RbacPrincipal(tenantId, userId);
        permissions.authorize(actor, "STAFF_LEAVE_APPLY", permissions.ownScope(actor), RbacTarget.NONE);
        StaffProfile staff = staffProfiles.findByIdTenantIdAndUserAccountId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff profile not found"));
        UUID staffId = staff.getStaffProfileId();
        StaffAssignment assignment = activeAssignment(tenantId, staffId);
        UUID institutionId = assignment.getInstitutionId();
        UUID departmentId = assignment.getDepartmentId();
        if (institutionId == null) {
            throw new BusinessRuleViolationException("STAFF_INSTITUTION_REQUIRED", "A staff institution assignment is required to submit leave.");
        }
        Map<String, Object> type = leaveType(tenantId, request.leaveTypeId());
        if (!"STAFF".equals(type.get("audience")) || !"ACTIVE".equals(type.get("status"))
                || !institutionId.equals(type.get("institution_id"))) {
            throw new BusinessRuleViolationException("INVALID_STAFF_LEAVE_TYPE", "Select an active staff leave type for your institution.");
        }
        BigDecimal days = workingDays(tenantId, institutionId, request.startDate(), request.endDate());
        if ("HALF_DAY".equals(unit)) days = days.subtract(BigDecimal.ONE).add(new BigDecimal("0.5"));
        if (days.signum() <= 0) {
            throw new BusinessRuleViolationException("NO_WORKING_DAYS", "The selected date range contains no working days.");
        }
        if (unit.equals("HALF_DAY") && !request.startDate().equals(request.endDate())) {
            throw new BusinessRuleViolationException("HALF_DAY_SINGLE_DATE", "A half-day request must cover one date.");
        }
        Integer overlaps = jdbc.queryForObject("""
                SELECT count(*) FROM app.leave_request
                 WHERE tenant_id=? AND for_staff_profile_id=? AND status IN ('PENDING','APPROVED')
                   AND start_date<=? AND end_date>=?
                """, Integer.class, tenantId, staffId, request.endDate(), request.startDate());
        if (overlaps != null && overlaps > 0) {
            throw new BusinessRuleViolationException("LEAVE_DATE_OVERLAP", "An existing pending or approved leave request overlaps these dates.");
        }
        boolean tracksBalance = Boolean.TRUE.equals(type.get("tracks_balance"));
        boolean isLossOfPay = Boolean.TRUE.equals(type.get("is_loss_of_pay"));
        BigDecimal available = ZERO;
        if (tracksBalance) {
            List<BigDecimal> amounts = jdbc.query("""
                    SELECT credited_days-used_days FROM app.leave_balance
                     WHERE tenant_id=? AND staff_profile_id=? AND leave_type_id=? AND leave_year=? FOR UPDATE
                    """, (rs, row) -> rs.getBigDecimal(1), tenantId, staffId, request.leaveTypeId(), request.startDate().getYear());
            if (!amounts.isEmpty()) available = amounts.get(0);
        }
        if (request.designatedDelegateStaffProfileId() != null) {
            if (request.designatedDelegateStaffProfileId().equals(staffId)) {
                throw new BusinessRuleViolationException("SELF_LEAVE_COVER_PROHIBITED", "A staff member cannot designate themselves as leave cover.");
            }
            StaffAssignment delegateAssignment = activeAssignment(tenantId, request.designatedDelegateStaffProfileId());
            if (!institutionId.equals(delegateAssignment.getInstitutionId())) {
                throw new BusinessRuleViolationException("LEAVE_COVER_SCOPE_MISMATCH", "Leave cover must be assigned within the same institution.");
            }
            Integer delegateOverlap = jdbc.queryForObject("""
                    SELECT count(*) FROM app.leave_request
                     WHERE tenant_id=? AND for_staff_profile_id=? AND status IN ('PENDING','APPROVED')
                       AND start_date<=? AND end_date>=?
                    """, Integer.class, tenantId, request.designatedDelegateStaffProfileId(), request.endDate(), request.startDate());
            if (delegateOverlap != null && delegateOverlap > 0) {
                throw new BusinessRuleViolationException("LEAVE_COVER_UNAVAILABLE", "The designated leave cover has overlapping leave.");
            }
        }
        BigDecimal lossOfPay = isLossOfPay ? days : (tracksBalance ? days.subtract(available).max(ZERO) : ZERO);
        UUID requestId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        List<StepSpec> steps = buildApprovalSteps(tenantId, staff, assignment, institutionId);
        boolean blocked = steps.isEmpty();
        String process = "STAFF_LEAVE";
        String activeRole = TenantContext.currentActiveRoleCode();
        if (activeRole == null || activeRole.isBlank()) {
            throw new BusinessRuleViolationException("ACTIVE_ROLE_REQUIRED", "Choose an active role before submitting leave.");
        }
        ApprovalRequest approval = ApprovalRequest.builder()
                .id(new ApprovalRequestId(tenantId, approvalId))
                .institutionId(institutionId)
                .processCode(process)
                .subjectType("STAFF_LEAVE_REQUEST")
                .subjectId(requestId)
                .requestedByUserId(userId)
                .requesterActiveRole(activeRole)
                .status(blocked ? "BLOCKED" : "PENDING")
                .currentStepNo((short) 1)
                .createdBy(userId).updatedBy(userId).build();
        approvalRequests.save(approval);
        if (!blocked) {
            short stepNo = 1;
            for (StepSpec spec : steps) {
                approvalSteps.save(ApprovalStep.builder()
                        .id(new ApprovalStepId(tenantId, UUID.randomUUID()))
                        .approvalRequestId(approvalId).stepNo(stepNo++)
                        .approverRoleCode(spec.roleCode()).approverUserId(spec.userId())
                        .decision("PENDING").createdBy(userId).updatedBy(userId).build());
            }
        }
        jdbc.update("""
                INSERT INTO app.leave_request
                  (tenant_id,leave_request_id,institution_id,requested_by_user_id,for_staff_profile_id,
                   leave_type_id,unit,half_day_part,start_date,end_date,reason,document_file_id,days_counted,
                   loss_of_pay_days,process_code,approval_request_id,status,submitted_at,
                   designated_delegate_staff_profile_id,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now(),?,?,?)
                """, tenantId, requestId, institutionId, userId, staffId, request.leaveTypeId(),
                unit, request.halfDayPart(), request.startDate(), request.endDate(), request.reason().trim(),
                request.documentFileId(), days, lossOfPay, process, approvalId, blocked ? "BLOCKED" : "PENDING",
                request.designatedDelegateStaffProfileId(), userId, userId);
        return new LeaveRequestResponse(requestId, request.leaveTypeId(), unit, request.startDate(),
                request.endDate(), request.reason().trim(), days, lossOfPay, process,
                blocked ? "BLOCKED" : "PENDING", approvalId, java.time.Instant.now());
    }

    @Transactional
    public void confirmLossOfPay(UUID tenantId, UUID actorId, UUID leaveRequestId) {
        Map<String, Object> row = leaveRequest(tenantId, leaveRequestId);
        UUID institutionId = (UUID) row.get("institution_id");
        RbacPrincipal actor = new RbacPrincipal(tenantId, actorId);
        permissions.authorize(actor, "LOSS_OF_PAY_CONFIRM",
                institutionId == null ? RbacScope.tenant() : permissions.targetScope(actor, institutionId, null),
                RbacTarget.of((UUID) row.get("requested_by_user_id")));
        if (!"PENDING".equals(row.get("status")) || ((BigDecimal) row.get("loss_of_pay_days")).signum() <= 0) {
            throw new BusinessRuleViolationException("LOSS_OF_PAY_NOT_PENDING", "Only a pending request with loss-of-pay days can be confirmed.");
        }
        jdbc.update("""
                UPDATE app.leave_request SET loss_of_pay_confirmed_by=?,loss_of_pay_confirmed_at=now(),
                       updated_by=? WHERE tenant_id=? AND leave_request_id=?
                """, actorId, actorId, tenantId, leaveRequestId);
    }

    @Transactional
    public void cancelOwn(UUID tenantId, UUID actorId, UUID leaveRequestId) {
        Map<String, Object> row = leaveRequest(tenantId, leaveRequestId);
        if (!actorId.equals(row.get("requested_by_user_id"))) {
            throw new ResourceNotFoundException("Leave request not found");
        }
        if (!"PENDING".equals(row.get("status")) && !"BLOCKED".equals(row.get("status"))) {
            throw new BusinessRuleViolationException("LEAVE_NOT_CANCELLABLE", "Only a pending or blocked leave request can be cancelled.");
        }
        LocalDate start = ((java.sql.Date) row.get("start_date")).toLocalDate();
        if (!start.isAfter(LocalDate.now())) {
            throw new BusinessRuleViolationException("FINAL_APPROVER_REQUIRED", "After the leave start date, cancellation requires the final approver.");
        }
        RbacPrincipal actor = new RbacPrincipal(tenantId, actorId);
        permissions.authorize(actor, "LEAVE_CANCEL_OWN", permissions.ownScope(actor), RbacTarget.NONE);
        cancelRows(tenantId, leaveRequestId, actorId, (UUID) row.get("approval_request_id"));
    }

    @Transactional
    public void cancelByFinalApprover(UUID tenantId, UUID actorId, UUID leaveRequestId) {
        Map<String, Object> row = leaveRequest(tenantId, leaveRequestId);
        if (!"PENDING".equals(row.get("status"))) {
            throw new BusinessRuleViolationException("LEAVE_NOT_CANCELLABLE", "Only pending leave can be cancelled by the final approver.");
        }
        UUID institutionId = (UUID) row.get("institution_id");
        UUID approvalId = (UUID) row.get("approval_request_id");
        var approval = approvalRequests.findByIdTenantIdAndIdApprovalRequestId(tenantId, approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found"));
        var step = approvalSteps.findByIdTenantIdAndApprovalRequestIdAndStepNo(
                tenantId, approvalId, approval.getCurrentStepNo())
                .orElseThrow(() -> new ResourceNotFoundException("Final approval step not found"));
        String action = com.edu360.api.approval.ApprovalInboxService.resolveApprovalAction(approval, step);
        if (action == null) throw new BusinessRuleViolationException("FINAL_APPROVER_REQUIRED", "The current leave approval step has no configured final-approver action.");
        RbacPrincipal actor = new RbacPrincipal(tenantId, actorId);
        permissions.authorize(actor, action,
                institutionId == null ? RbacScope.tenant() : permissions.targetScope(actor, institutionId, null),
                row.get("requested_by_user_id") == null ? RbacTarget.NONE : RbacTarget.of((UUID) row.get("requested_by_user_id")));
        cancelRows(tenantId, leaveRequestId, actorId, approvalId);
    }

    private void cancelRows(UUID tenantId, UUID leaveRequestId, UUID actorId, UUID approvalId) {
        jdbc.update("UPDATE app.leave_request SET status='CANCELLED',cancelled_at=now(),cancelled_by=?,updated_by=? WHERE tenant_id=? AND leave_request_id=?",
                actorId, actorId, tenantId, leaveRequestId);
        jdbc.update("UPDATE app.approval_request SET status='CANCELLED',final_decided_at=now(),updated_by=? WHERE tenant_id=? AND approval_request_id=? AND status IN ('PENDING','BLOCKED')",
                actorId, tenantId, approvalId);
        jdbc.update("""
                UPDATE app.approval_step SET decision='SKIPPED',skip_reason='Leave request cancelled',
                       acted_by_user_id=?,acted_active_role=?,decided_at=now(),updated_by=?
                 WHERE tenant_id=? AND approval_request_id=? AND decision='PENDING'
                """, actorId, TenantContext.currentActiveRoleCode(), actorId, tenantId, approvalId);
    }

    private Map<String, Object> leaveRequest(UUID tenantId, UUID requestId) {
        try {
            return jdbc.queryForMap("""
                    SELECT leave_request_id,institution_id,requested_by_user_id,for_staff_profile_id,
                           leave_type_id,start_date,end_date,days_counted,loss_of_pay_days,
                           loss_of_pay_confirmed_at,approval_request_id,status,designated_delegate_staff_profile_id
                      FROM app.leave_request WHERE tenant_id=? AND leave_request_id=?
                    """, tenantId, requestId);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("Leave request not found");
        }
    }

    private Map<String, Object> leaveType(UUID tenantId, UUID leaveTypeId) {
        try {
            return jdbc.queryForMap("""
                    SELECT institution_id,audience,tracks_balance,is_loss_of_pay,status
                      FROM app.leave_type WHERE tenant_id=? AND leave_type_id=?
                    """, tenantId, leaveTypeId);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("Leave type not found");
        }
    }

    private UUID activeInstitution(UUID tenantId, UUID staffId) {
        return activeAssignment(tenantId, staffId).getInstitutionId();
    }

    private StaffAssignment activeAssignment(UUID tenantId, UUID staffId) {
        List<StaffAssignment> active = staffAssignments.findByTenantIdAndStaffProfileIdOrderByEffectiveFromDesc(tenantId, staffId)
                .stream().filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()))
                .filter(a -> a.getEffectiveTo() == null || !a.getEffectiveTo().isBefore(LocalDate.now()))
                .toList();
        if (active.isEmpty()) throw new ResourceNotFoundException("Active staff institution assignment not found");
        if (active.size() == 1) return active.get(0);
        UUID chosenInstitution = TenantContext.currentInstitutionIds().stream().findFirst().orElse(null);
        return active.stream().filter(a -> chosenInstitution != null && chosenInstitution.equals(a.getInstitutionId()))
                .findFirst().orElseThrow(() -> new BusinessRuleViolationException("STAFF_ASSIGNMENT_AMBIGUOUS", "A single active staff institution assignment is required."));
    }

    private List<StepSpec> buildApprovalSteps(UUID tenantId, StaffProfile staff, StaffAssignment assignment, UUID institutionId) {
        String role = TenantContext.currentActiveRoleCode();
        if ("PRINCIPAL_COLLEGE".equalsIgnoreCase(role) || "SCHOOL_PRINCIPAL".equalsIgnoreCase(role)) {
            LeaveApproverSetting setting = approverSettings
                    .findByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(tenantId, "PRINCIPAL", institutionId, "ACTIVE")
                    .orElse(null);
            if (setting == null) return List.of();
            return List.of(new StepSpec(setting.getApproverKind(), setting.getApproverUserId()));
        }
        if ("VICE_CHANCELLOR".equalsIgnoreCase(role)) {
            LeaveApproverSetting setting = approverSettings.findByIdTenantIdAndSubjectRoleAndStatus(
                    tenantId, "VICE_CHANCELLOR", "ACTIVE").orElse(null);
            return setting == null ? List.of() : List.of(new StepSpec(setting.getApproverKind(), setting.getApproverUserId()));
        }
        if ("REGISTRAR".equalsIgnoreCase(role)) {
            return List.of(new StepSpec("UNIVERSITY_HR_MANAGER", null), new StepSpec("VICE_CHANCELLOR", null));
        }
        Institution institution = institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found"));
        List<StepSpec> chain = new ArrayList<>();
        boolean universityStaff = assignment.getInstitutionId() == null;
        boolean school = institution.getInstitutionType() != null
                && institution.getInstitutionType().toUpperCase().contains("SCHOOL");
        boolean teaching = isTeaching(assignment.getDesignation(), role);
        if (universityStaff) {
            chain.add(new StepSpec("UNIVERSITY_HR_MANAGER", null));
        } else if (!school && !teaching) {
            chain.add(new StepSpec("HR_MANAGER", null));
        } else if (!school && assignment.getDepartmentId() != null) {
            UUID hodUser = jdbc.query("""
                    SELECT sa2.user_account_id
                      FROM app.department d
                      JOIN app.staff_assignment sa2 ON sa2.tenant_id=d.tenant_id
                       AND sa2.staff_assignment_id=d.hod_staff_assignment_id
                     WHERE d.tenant_id=? AND d.department_id=? AND sa2.status='ACTIVE'
                    """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, tenantId, assignment.getDepartmentId());
            if (hodUser == null) return List.of();
            chain.add(new StepSpec("HEAD_OF_DEPARTMENT", hodUser));
        }
        if (universityStaff) {
            chain.add(new StepSpec("REGISTRAR", null));
        } else {
            chain.add(new StepSpec(school ? "SCHOOL_PRINCIPAL" : "PRINCIPAL_COLLEGE", null));
        }
        return chain;
    }

    private static boolean isTeaching(String designation, String activeRole) {
        String value = ((designation == null ? "" : designation) + " " + (activeRole == null ? "" : activeRole)).toUpperCase();
        return value.contains("FACULTY") || value.contains("PROFESSOR") || value.contains("TEACHER")
                || value.contains("LECTURER") || value.contains("HOD") || value.contains("HEAD_OF_DEPARTMENT");
    }

    private BigDecimal workingDays(UUID tenantId, UUID institutionId, LocalDate start, LocalDate end) {
        List<java.sql.Date[]> holidays = jdbc.query("""
                SELECT start_date,end_date FROM app.academic_calendar_event
                 WHERE tenant_id=? AND (institution_id=? OR (institution_id IS NULL AND source_level='UNIVERSITY'))
                   AND event_type IN ('LOCAL_HOLIDAY','UNIVERSITY_HOLIDAY')
                   AND start_date<=? AND end_date>=?
                """, (rs, row) -> new java.sql.Date[] { rs.getDate(1), rs.getDate(2) }, tenantId, institutionId, end, start);
        BigDecimal days = ZERO;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            LocalDate currentDate = date;
            if (currentDate.getDayOfWeek() == DayOfWeek.SATURDAY || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            boolean holiday = holidays.stream().anyMatch(range ->
                    !currentDate.isBefore(range[0].toLocalDate()) && !currentDate.isAfter(range[1].toLocalDate()));
            if (!holiday) days = days.add(BigDecimal.ONE);
        }
        return days.setScale(1, RoundingMode.HALF_UP);
    }

    private record StepSpec(String roleCode, UUID userId) {}
}
