package com.edu360.api.academic.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.exception.UnauthorizedException;

@Service
public class MedicalExemptionService {
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    public MedicalExemptionService(JdbcTemplate jdbc,PermissionEvaluatorService permissions){this.jdbc=jdbc;this.permissions=permissions;}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> myEnrollments(UUID tenantId,TenantPrincipal actor){
        RbacPrincipal rbac=rbac(tenantId,actor);
        var active=permissions.activeAssignment(rbac).orElseThrow(
            ()->new com.edu360.api.exception.UnauthorizedException("An active student role is required."));
        permissions.authorize(rbac,"STUDENT_LEAVE_APPLY",PermissionEvaluatorService.scopeOf(active),RbacTarget.NONE);
        return jdbc.queryForList("""
            WITH eligible_students AS (
             SELECT p.student_profile_id FROM app.student_profile p
              WHERE p.tenant_id=? AND p.user_account_id=? AND p.status='ACTIVE'
             UNION
             SELECT gl.student_profile_id FROM app.guardian_profile gp
              JOIN app.student_guardian_link gl ON gl.tenant_id=gp.tenant_id
               AND gl.guardian_profile_id=gp.guardian_profile_id
              WHERE gp.tenant_id=? AND gp.user_account_id=? AND gl.status='ACTIVE' AND gl.is_primary
            )
            SELECT e.enrollment_id,e.institution_id,e.academic_session_id,e.section_id,
                   s.section_name,a.academic_year
              FROM eligible_students es JOIN app.student_enrollment e ON e.tenant_id=? AND e.student_profile_id=es.student_profile_id
              JOIN app.section s ON s.tenant_id=e.tenant_id AND s.institution_id=e.institution_id AND s.section_id=e.section_id
              JOIN app.academic_session a ON a.tenant_id=e.tenant_id AND a.academic_session_id=e.academic_session_id
             WHERE e.outcome='IN_PROGRESS' AND e.ended_on IS NULL
             ORDER BY a.start_date DESC,s.section_name
            """,tenantId,actor.userAccountId(),tenantId,actor.userAccountId(),tenantId);
    }

    @Transactional
    public Map<String,Object> request(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID enrollmentId,
            LocalDate startDate,LocalDate endDate,UUID documentRef){
        authorize(tenantId,actor,"STUDENT_LEAVE_APPLY",institutionId,departmentForEnrollment(tenantId,institutionId,enrollmentId));
        UUID studentId=jdbc.query("""
            SELECT e.student_profile_id FROM app.student_enrollment e
              JOIN app.student_profile p ON p.tenant_id=e.tenant_id AND p.institution_id=e.institution_id
               AND p.student_profile_id=e.student_profile_id
             WHERE e.tenant_id=? AND e.institution_id=? AND e.enrollment_id=?
              AND e.outcome='IN_PROGRESS' AND e.ended_on IS NULL
              AND (p.user_account_id=? OR EXISTS(
               SELECT 1 FROM app.guardian_profile gp
                JOIN app.student_guardian_link gl ON gl.tenant_id=gp.tenant_id
                 AND gl.guardian_profile_id=gp.guardian_profile_id
                WHERE gp.tenant_id=e.tenant_id AND gp.user_account_id=?
                 AND gl.student_profile_id=e.student_profile_id AND gl.status='ACTIVE' AND gl.is_primary
              ))
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,enrollmentId,actor.userAccountId(),actor.userAccountId());
        if(studentId==null)throw new ResourceNotFoundException("The enrolment is not an active enrolment for this student or primary guardian.");
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.attendance_medical_exemption(tenant_id,attendance_medical_exemption_id,institution_id,
             enrollment_id,student_profile_id,start_date,end_date,document_ref,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,tenantId,id,institutionId,enrollmentId,studentId,startDate,endDate,documentRef,actor.userAccountId(),actor.userAccountId());
        return jdbc.queryForMap("SELECT * FROM app.attendance_medical_exemption WHERE tenant_id=? AND attendance_medical_exemption_id=?",tenantId,id);
    }

    @Transactional
    public Map<String,Object> decide(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID exemptionId,
            String decision,String note){
        if(!List.of("APPROVED","REJECTED").contains(decision))throw new IllegalArgumentException("Decision must be APPROVED or REJECTED.");
        Map<String,Object> row;
        try{row=jdbc.queryForMap("""
            SELECT e.*,e.created_by AS requester_user_id,e.enrollment_id AS request_enrollment_id FROM app.attendance_medical_exemption e
             WHERE e.tenant_id=? AND e.institution_id=? AND e.attendance_medical_exemption_id=?
            """,tenantId,institutionId,exemptionId);}
        catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Medical exemption request not found.");}
        UUID requester=(UUID)row.get("requester_user_id");
        authorize(tenantId,actor,"ATTENDANCE_CORRECTION_APPROVE",institutionId,departmentForEnrollment(tenantId,institutionId,(UUID)row.get("request_enrollment_id")),
                requester==null?RbacTarget.NONE:RbacTarget.of(requester));
        int changed=jdbc.update("""
            UPDATE app.attendance_medical_exemption SET status=?,approved_by_user_id=?,decided_at=now(),
             decision_note=?,updated_by=? WHERE tenant_id=? AND institution_id=? AND attendance_medical_exemption_id=? AND status='PENDING'
            """,decision,actor.userAccountId(),note,actor.userAccountId(),tenantId,institutionId,exemptionId);
        if(changed==0)throw new BusinessRuleViolationException("MEDICAL_EXEMPTION_DECISION_IMMUTABLE","This medical exemption has already been decided.");
        return jdbc.queryForMap("SELECT * FROM app.attendance_medical_exemption WHERE tenant_id=? AND attendance_medical_exemption_id=?",tenantId,exemptionId);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID enrollmentId,UUID departmentId){
        UUID scopeDepartment=departmentId!=null?departmentId:enrollmentId==null?null:departmentForEnrollment(tenantId,institutionId,enrollmentId);
        authorize(tenantId,actor,"ATTENDANCE_VIEW",institutionId,scopeDepartment);
        return jdbc.queryForList("""
            SELECT x.attendance_medical_exemption_id,x.enrollment_id,x.student_profile_id,x.start_date,x.end_date,
                   x.document_ref,x.status,x.requested_at,x.approved_by_user_id,x.decided_at,x.decision_note,
                   e.section_id,s.section_name,pr.department_id
              FROM app.attendance_medical_exemption x
              JOIN app.student_enrollment e ON e.tenant_id=x.tenant_id AND e.institution_id=x.institution_id AND e.enrollment_id=x.enrollment_id
              JOIN app.section s ON s.tenant_id=e.tenant_id AND s.institution_id=e.institution_id AND s.section_id=e.section_id
              LEFT JOIN app.semester sm ON sm.tenant_id=s.tenant_id AND sm.semester_id=s.semester_id
              LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id
              LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id
             WHERE x.tenant_id=? AND x.institution_id=?
               AND (?::uuid IS NULL OR x.enrollment_id=?)
               AND (?::uuid IS NULL OR pr.department_id=?)
             ORDER BY x.requested_at DESC
            """,tenantId,institutionId,enrollmentId,enrollmentId,scopeDepartment,scopeDepartment);
    }

    private void authorize(UUID tenantId,TenantPrincipal actor,String action,UUID institutionId,UUID departmentId){
        authorize(tenantId,actor,action,institutionId,departmentId,RbacTarget.NONE);
    }

    private void authorize(UUID tenantId,TenantPrincipal actor,String action,UUID institutionId,UUID departmentId,RbacTarget target){
        RbacPrincipal principal=rbac(tenantId,actor);
        UserRoleAssignment active=permissions.activeAssignment(principal)
            .orElseThrow(()->new UnauthorizedException("An active role is required for medical exemption access."));
        RbacScope scope=switch(active.getScopeType()){
            case TENANT -> RbacScope.tenant();
            case INSTITUTION -> RbacScope.institution(institutionId);
            case DEPARTMENT -> {
                if(departmentId==null)throw new UnauthorizedException("This medical exemption operation requires a department-scoped target.");
                yield RbacScope.department(institutionId,departmentId);
            }
        };
        permissions.authorize(principal,action,scope,target);
    }

    private UUID departmentForEnrollment(UUID tenantId,UUID institutionId,UUID enrollmentId){
        UUID sectionId=jdbc.query("""
            SELECT section_id FROM app.student_enrollment WHERE tenant_id=? AND institution_id=? AND enrollment_id=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,enrollmentId);
        if(sectionId==null)return null;
        return jdbc.query("""
            SELECT pr.department_id FROM app.section s
              LEFT JOIN app.semester sm ON sm.tenant_id=s.tenant_id AND sm.semester_id=s.semester_id
              LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id
              LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id
             WHERE s.tenant_id=? AND s.institution_id=? AND s.section_id=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,sectionId);
    }

    private static RbacPrincipal rbac(UUID tenantId,TenantPrincipal actor){return new RbacPrincipal(tenantId,actor.userAccountId());}
}