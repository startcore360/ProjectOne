package com.edu360.api.files;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.ApprovalRequestRepository;
import com.edu360.api.approval.ApprovalRouting;
import com.edu360.api.approval.ApprovalScopeResolver;
import com.edu360.api.approval.ApprovalStep;
import com.edu360.api.approval.ApprovalStepRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import org.springframework.stereotype.Component;

import com.edu360.api.config.TenantPrincipal;

/**
 * Resolves the owning domain row before FileService performs the matching action/scope check.
 * Every query is tenant-qualified; personal submission access includes only the student or an
 * ACTIVE linked guardian, while the assignment author may review submitted work.
 */
@Component
public class JdbcFileAccessPolicy implements FileAccessPolicy {
    private static final String STUDY_MATERIAL = """
            SELECT EXISTS (
              SELECT 1
              FROM app.study_material m
              WHERE m.tenant_id = ? AND m.file_id = ? AND m.status = 'ACTIVE'
                AND (
                  m.uploaded_by = ?
                  OR EXISTS (
                    SELECT 1
                    FROM app.subject_allocation sa
                    JOIN app.staff_assignment st
                      ON st.tenant_id = sa.tenant_id
                     AND st.staff_assignment_id = sa.staff_assignment_id
                    JOIN app.staff_profile sp
                      ON sp.tenant_id = st.tenant_id
                     AND sp.staff_profile_id = st.staff_profile_id
                    WHERE sa.tenant_id = m.tenant_id
                      AND sa.institution_id = m.institution_id
                      AND sa.course_offering_id = m.course_offering_id
                      AND sa.status = 'ACTIVE'
                      AND sp.user_account_id = ?
                      AND ((m.section_id IS NOT NULL AND sa.section_id = m.section_id)
                        OR (m.subject_group_id IS NOT NULL AND sa.subject_group_id = m.subject_group_id))
                  )
                  OR EXISTS (
                    SELECT 1
                    FROM app.course_offering co
                    JOIN app.student_enrollment en
                      ON en.tenant_id = co.tenant_id
                     AND en.institution_id = co.institution_id
                     AND en.academic_session_id = co.academic_session_id
                     AND en.outcome = 'IN_PROGRESS'
                     AND en.started_on <= CURRENT_DATE
                     AND (en.ended_on IS NULL OR en.ended_on >= CURRENT_DATE)
                    JOIN app.student_profile student
                      ON student.tenant_id = en.tenant_id
                     AND student.student_profile_id = en.student_profile_id
                     AND student.status = 'ACTIVE'
                    WHERE co.tenant_id = m.tenant_id
                      AND co.institution_id = m.institution_id
                      AND co.course_offering_id = m.course_offering_id
                      AND (
                        (m.section_id IS NOT NULL AND en.section_id = m.section_id)
                        OR (m.subject_group_id IS NOT NULL AND EXISTS (
                          SELECT 1
                          FROM app.subject_group_member gm
                          JOIN app.subject_group sg
                            ON sg.tenant_id = gm.tenant_id
                           AND sg.subject_group_id = gm.subject_group_id
                          WHERE gm.tenant_id = en.tenant_id
                            AND gm.enrollment_id = en.enrollment_id
                            AND gm.subject_group_id = m.subject_group_id
                            AND sg.course_offering_id = co.course_offering_id
                            AND sg.status = 'ACTIVE'
                        ))
                      )
                      AND (
                        student.user_account_id = ?
                        OR EXISTS (
                          SELECT 1
                          FROM app.student_guardian_link gl
                          JOIN app.guardian_profile guardian
                            ON guardian.tenant_id = gl.tenant_id
                           AND guardian.guardian_profile_id = gl.guardian_profile_id
                          WHERE gl.tenant_id = student.tenant_id
                            AND gl.student_profile_id = student.student_profile_id
                            AND gl.status = 'ACTIVE'
                            AND guardian.user_account_id = ?
                        )
                      )
                  )
                )
            )
            """;
    private static final String ASSIGNMENT_SUBMISSION = """
            SELECT EXISTS (
              SELECT 1
              FROM app.assignment_submission_file sf
              JOIN app.assignment_submission sub
                ON sub.tenant_id = sf.tenant_id AND sub.submission_id = sf.submission_id
              JOIN app.assignment a
                ON a.tenant_id = sub.tenant_id AND a.assignment_id = sub.assignment_id
              JOIN app.student_enrollment en
                ON en.tenant_id = sub.tenant_id AND en.enrollment_id = sub.enrollment_id
              JOIN app.student_profile student
                ON student.tenant_id = en.tenant_id AND student.student_profile_id = en.student_profile_id
              WHERE sf.tenant_id = ? AND sf.file_id = ?
                AND sub.status IN ('SUBMITTED', 'EVALUATED')
                AND (
                  student.user_account_id = ?
                  OR a.created_by_user_id = ?
                  OR EXISTS (
                    SELECT 1
                    FROM app.student_guardian_link gl
                    JOIN app.guardian_profile guardian
                      ON guardian.tenant_id = gl.tenant_id
                     AND guardian.guardian_profile_id = gl.guardian_profile_id
                    WHERE gl.tenant_id = student.tenant_id
                      AND gl.student_profile_id = student.student_profile_id
                      AND gl.status = 'ACTIVE'
                      AND guardian.user_account_id = ?
                  )
                )
            )
            """;
    private static final String SYLLABUS = """
            SELECT EXISTS (
              SELECT 1 FROM app.syllabus s
              WHERE s.tenant_id = ? AND s.file_id = ? AND s.status = 'PUBLISHED'
            )
            """;
    private static final String STAFF_DOCUMENT = """
            SELECT EXISTS (
              SELECT 1
              FROM app.staff_document d
              JOIN app.staff_profile p
                ON p.tenant_id = d.tenant_id AND p.staff_profile_id = d.staff_profile_id
              WHERE d.tenant_id = ? AND d.file_id = ?
            )
            """;
    private static final String ADMISSION_DOCUMENT = """
            SELECT EXISTS (
              SELECT 1 FROM app.admission_application a
              CROSS JOIN LATERAL jsonb_array_elements(a.documents_json) d
              WHERE a.tenant_id = ? AND (d->>'fileId')::uuid = ? AND a.created_by = ?
            )
            """;
    private static final String ATTENDANCE_UPLOAD = """
            SELECT EXISTS (
              SELECT 1 FROM app.import_job j
              WHERE j.tenant_id = ? AND j.source_file_id = ?
                AND j.import_kind = 'ATTENDANCE'
            )
            """;
    private static final String MEDICAL_EXEMPTION = """
            SELECT EXISTS (
              SELECT 1 FROM app.attendance_medical_exemption e
              JOIN app.student_profile p
                ON p.tenant_id=e.tenant_id AND p.student_profile_id=e.student_profile_id
              WHERE e.tenant_id=? AND e.document_ref=?
                AND (p.user_account_id=? OR EXISTS (
                  SELECT 1 FROM app.student_guardian_link gl
                  JOIN app.guardian_profile gp ON gp.tenant_id=gl.tenant_id
                   AND gp.guardian_profile_id=gl.guardian_profile_id
                  WHERE gl.tenant_id=e.tenant_id AND gl.student_profile_id=e.student_profile_id
                   AND gl.status='ACTIVE' AND gl.is_primary AND gp.user_account_id=?
                ))
            )
            """;

    static final String STAFF_ATTENDANCE_REVIEW_ACTION = "STAFF_ATTENDANCE_CHECKIN_REVIEW";
    static final String STAFF_ATTENDANCE_REVIEW_PROCESS = "STAFF_ATTENDANCE_REVIEW";

    private final JdbcTemplate jdbc;

    @Autowired(required = false)
    private PermissionEvaluatorService permissions;

    // Optional like `permissions`: used only to recognise the routed approver of a check-in selfie.
    @Autowired(required = false)
    private ApprovalRouting approvalRouting;

    @Autowired(required = false)
    private ApprovalRequestRepository approvalRequests;

    @Autowired(required = false)
    private ApprovalStepRepository approvalSteps;

    // The scope the engine checks the review action at (institution, or the check-in's department
    // for an HOD). Missing -> the routed-approver path denies (fail closed).
    @Autowired(required = false)
    private ApprovalScopeResolver approvalScopeResolver;

    public JdbcFileAccessPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean canAccess(FileObject file, TenantPrincipal principal) {
        UUID tenantId = principal.tenantId();
        UUID userId = principal.userAccountId();
        UUID fileId = file.getId().getFileId();
        return switch (FilePurpose.valueOf(file.getPurpose())) {
            case STUDY_MATERIAL -> exists(STUDY_MATERIAL, tenantId, fileId, userId, userId, userId, userId)
                    || canStaffViewStudyMaterial(file, principal);
            case ASSIGNMENT_SUBMISSION -> exists(ASSIGNMENT_SUBMISSION, tenantId, fileId, userId, userId, userId);
            case SYLLABUS -> exists(SYLLABUS, tenantId, fileId);
            case STAFF_DOCUMENT -> exists(STAFF_DOCUMENT, tenantId, fileId);
            case ATTENDANCE_UPLOAD -> exists(ATTENDANCE_UPLOAD, tenantId, fileId);
            case ATTENDANCE_MEDICAL_EXEMPTION -> exists(MEDICAL_EXEMPTION, tenantId, fileId, userId, userId)
                    || canViewMedicalExemption(file, principal);
            case ADMISSION_DOCUMENT -> exists(ADMISSION_DOCUMENT, tenantId, fileId, userId)
                    || canVerifyAdmissionDocument(file, principal);
            case GRIEVANCE_EVIDENCE -> exists("""
                    SELECT EXISTS (
                      SELECT 1 FROM app.grievance_evidence ge
                      JOIN app.grievance_case gc ON gc.tenant_id=ge.tenant_id
                       AND gc.grievance_case_id=ge.grievance_case_id
                      WHERE ge.tenant_id=? AND ge.file_id=? AND gc.institution_id=?
                        AND app.fn_complaint_guard(gc.tenant_id,gc.accused_user_id,gc.institution_id)
                    )
                    """, tenantId, fileId, file.getInstitutionId());
            case DISCIPLINARY_EVIDENCE -> exists("""
                    SELECT EXISTS (
                      SELECT 1 FROM app.disciplinary_evidence de
                      JOIN app.disciplinary_incident di ON di.tenant_id=de.tenant_id
                       AND di.disciplinary_incident_id=de.disciplinary_incident_id
                      WHERE de.tenant_id=? AND de.file_id=? AND di.institution_id=?
                    )
                    """, tenantId, fileId, file.getInstitutionId());
            case STAFF_ATTENDANCE_SELFIE -> canViewAttendanceSelfie(file, principal);
        };
    }

    /**
     * A check-in selfie is personal data: the staff member who took it may read it; otherwise
     * only someone who may decide that check-in - the currently routed approver of its pending
     * STAFF_ATTENDANCE_REVIEW step (role, named user, delegation or roll-down, exactly as the
     * approval engine routes it) who also holds STAFF_ATTENDANCE_CHECKIN_REVIEW at the scope the
     * engine checks (the institution, or the check-in's department for an HOD - so an HOD of
     * another department is denied even though the HOD role routes), or a holder of
     * STAFF_ATTENDANCE_CHECKIN_REVIEW at the check-in's institution scope (a department-scoped HOD
     * never matches that). A selfie not linked to any check-in is the owner's alone.
     * Everything else, including the requester's peers, is denied.
     */
    private boolean canViewAttendanceSelfie(FileObject file, TenantPrincipal principal) {
        UUID tenantId = principal.tenantId();
        UUID userId = principal.userAccountId();
        if (userId != null && userId.equals(file.getUploadedBy())) {
            return true;
        }
        SelfieCheckin checkin;
        try {
            checkin = jdbc.query("""
                    SELECT c.staff_user_id, c.institution_id, c.approval_request_id
                      FROM app.staff_attendance_checkin c
                     WHERE c.tenant_id = ? AND c.photo_file_id = ?
                    """, rs -> rs.next() ? new SelfieCheckin(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class)) : null, tenantId, file.getId().getFileId());
        } catch (org.springframework.dao.DataAccessException ex) {
            return false;
        }
        if (checkin == null || userId == null) {
            return false;
        }
        if (userId.equals(checkin.staffUserId())) {
            return true;
        }
        if (isRoutedCheckinApprover(tenantId, userId, checkin.approvalRequestId())) {
            return true;
        }
        if (permissions == null || checkin.institutionId() == null) {
            return false;
        }
        RbacPrincipal caller = new RbacPrincipal(tenantId, userId);
        return permissions.can(caller, STAFF_ATTENDANCE_REVIEW_ACTION,
                permissions.targetScope(caller, checkin.institutionId(), null),
                RbacTarget.of(checkin.staffUserId())).allowed();
    }

    private boolean isRoutedCheckinApprover(UUID tenantId, UUID userId, UUID approvalRequestId) {
        if (approvalRequestId == null || approvalRouting == null || approvalRequests == null || approvalSteps == null
                || approvalScopeResolver == null || permissions == null) {
            return false;
        }
        ApprovalRequest request = approvalRequests.findByIdTenantIdAndIdApprovalRequestId(tenantId, approvalRequestId)
                .orElse(null);
        if (request == null || !"PENDING".equals(request.getStatus())
                || !STAFF_ATTENDANCE_REVIEW_PROCESS.equalsIgnoreCase(request.getProcessCode())
                || userId.equals(request.getRequestedByUserId())) {
            return false;
        }
        ApprovalStep step = approvalSteps
                .findByIdTenantIdAndApprovalRequestIdAndStepNo(tenantId, approvalRequestId, request.getCurrentStepNo())
                .filter(s -> "PENDING".equals(s.getDecision()))
                .orElse(null);
        if (step == null) {
            return false;
        }
        String activeRole = TenantContext.isSet() ? TenantContext.currentActiveRoleCode() : null;
        ApprovalRouting.Route route = approvalRouting.route(tenantId, userId, activeRole, request, step).orElse(null);
        if (route == null) {
            return false;
        }
        // Same action and scope check the engine makes before a decision (delegator's rights when
        // acting by delegation), so whoever can open the selfie is exactly whoever can decide it.
        UUID checked = route.via() == ApprovalRouting.Via.DELEGATION ? route.delegatorUserId() : userId;
        if (checked == null) {
            return false;
        }
        RbacPrincipal rbac = new RbacPrincipal(tenantId, checked);
        return permissions.can(rbac, STAFF_ATTENDANCE_REVIEW_ACTION, approvalScopeResolver.scopeFor(rbac, request),
                RbacTarget.of(request.getRequestedByUserId())).allowed();
    }

    private record SelfieCheckin(UUID staffUserId, UUID institutionId, UUID approvalRequestId) {
    }

    @Override
    public boolean canAttach(FileObject file, TenantPrincipal principal, String recordType, UUID recordId) {
        if (recordId == null || !"ACTIVE".equals(file.getStatus())
                || !java.util.Set.of("PENDING", "CLEAN").contains(file.getScanStatus())
                || !principal.userAccountId().equals(file.getUploadedBy())) {
            return false;
        }
        if ("GRIEVANCE_CASE".equals(recordType)
                && "GRIEVANCE_EVIDENCE".equals(file.getPurpose())
                && file.getInstitutionId() != null) {
            return exists("""
                    SELECT EXISTS (
                      SELECT 1 FROM app.grievance_case gc
                      WHERE gc.tenant_id=? AND gc.grievance_case_id=?
                        AND gc.institution_id=?
                        AND app.fn_complaint_guard(gc.tenant_id,gc.accused_user_id,gc.institution_id)
                    )
                    """, principal.tenantId(), recordId, file.getInstitutionId());
        }
        if ("DISCIPLINARY_INCIDENT".equals(recordType)
                && "DISCIPLINARY_EVIDENCE".equals(file.getPurpose())
                && file.getInstitutionId() != null) {
            return exists("""
                    SELECT EXISTS (
                      SELECT 1 FROM app.disciplinary_incident di
                      WHERE di.tenant_id=? AND di.disciplinary_incident_id=?
                        AND di.institution_id=?
                    )
                    """, principal.tenantId(), recordId, file.getInstitutionId());
        }
        if (!"STAFF_DOCUMENT".equals(recordType) || !"STAFF_DOCUMENT".equals(file.getPurpose())) return false;
        return exists("""
                SELECT EXISTS (
                  SELECT 1 FROM app.staff_profile p
                  JOIN app.staff_assignment a
                    ON a.tenant_id = p.tenant_id
                   AND a.staff_profile_id = p.staff_profile_id
                   AND a.status = 'ACTIVE'
                  WHERE p.tenant_id = ? AND p.staff_profile_id = ?
                    AND (?::uuid IS NULL OR a.institution_id = ?)
                )
                """, principal.tenantId(), recordId, file.getInstitutionId(), file.getInstitutionId());
    }

    private boolean canViewMedicalExemption(FileObject file, TenantPrincipal principal) {
        if (permissions == null || file.getInstitutionId() == null) return false;
        UUID departmentId=jdbc.query("""
            SELECT pr.department_id
              FROM app.attendance_medical_exemption e
              JOIN app.student_enrollment en ON en.tenant_id=e.tenant_id AND en.institution_id=e.institution_id
               AND en.enrollment_id=e.enrollment_id
              JOIN app.section s ON s.tenant_id=en.tenant_id AND s.institution_id=en.institution_id AND s.section_id=en.section_id
              LEFT JOIN app.semester sm ON sm.tenant_id=s.tenant_id AND sm.semester_id=s.semester_id
              LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id
              LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id
             WHERE e.tenant_id=? AND e.document_ref=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,principal.tenantId(),file.getId().getFileId());
        RbacPrincipal caller=new RbacPrincipal(principal.tenantId(),principal.userAccountId());
        var active=permissions.activeAssignment(caller).orElse(null);
        if(active==null)return false;
        if("DEPARTMENT".equals(active.getScopeType().name())
                && (departmentId==null || !departmentId.equals(active.getDepartmentId())))return false;
        RbacScope scope=permissions.targetScope(caller,file.getInstitutionId(),departmentId);
        return permissions.can(caller,"ATTENDANCE_CORRECTION_APPROVE",scope,RbacTarget.NONE).allowed();
    }

    private boolean canVerifyAdmissionDocument(FileObject file, TenantPrincipal principal) {
        if (permissions == null || file.getInstitutionId() == null) return false;
        return permissions.can(new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "APPLICATION_VERIFY", RbacScope.institution(file.getInstitutionId()), RbacTarget.NONE).allowed();
    }

    private boolean canStaffViewStudyMaterial(FileObject file, TenantPrincipal principal) {
        if (permissions == null || principal.userType() != UserType.STAFF || file.getInstitutionId() == null) {
            return false;
        }
        return permissions.can(new RbacPrincipal(principal.tenantId(), principal.userAccountId()),
                "STUDY_MATERIAL_VIEW", RbacScope.institution(file.getInstitutionId()), RbacTarget.NONE).allowed();
    }

    private boolean exists(String sql, Object... parameters) {
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, parameters));
        } catch (org.springframework.dao.DataAccessException ex) {
            // Missing domain rows, RLS denials and database errors all fail closed.
            return false;
        }
    }
}
