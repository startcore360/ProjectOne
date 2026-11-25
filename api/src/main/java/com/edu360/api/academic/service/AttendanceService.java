package com.edu360.api.academic.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.academic.calculator.AttendancePercentageCalculator;
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
public class AttendanceService {
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final AttendancePercentageCalculator calculator;

    public AttendanceService(JdbcTemplate jdbc,PermissionEvaluatorService permissions,AttendancePercentageCalculator calculator){
        this.jdbc=jdbc;this.permissions=permissions;this.calculator=calculator;
    }

    @Transactional
    public Map<String,Object> createSession(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID academicSessionId,
            UUID sectionId,UUID slotId,LocalDate date,String sessionType,boolean notHeld,List<AttendanceItem> entries){
        RbacPrincipal rbac=rbac(tenantId,actor);
        authorize(tenantId,actor,notHeld?"ATTENDANCE_NOT_HELD":"ATTENDANCE_MARK",institutionId,departmentForSection(tenantId,institutionId,sectionId));
        UUID teacherId=jdbc.query("""
            SELECT staff_profile_id FROM app.staff_profile
             WHERE tenant_id=? AND user_account_id=? AND employment_status='ACTIVE'
             ORDER BY staff_profile_id LIMIT 1
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,actor.userAccountId());
        if(teacherId==null)throw new BusinessRuleViolationException("TEACHER_PROFILE_REQUIRED","An active staff profile is required to mark attendance.");
        UUID sessionId=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.attendance_session(tenant_id,attendance_session_id,institution_id,academic_session_id,
             section_id,timetable_slot_id,session_date,taken_by_teacher_id,session_type,session_status,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """,tenantId,sessionId,institutionId,academicSessionId,sectionId,slotId,date,teacherId,sessionType,
            notHeld?"NOT_HELD":"HELD",actor.userAccountId(),actor.userAccountId());
        for(AttendanceItem item:entries){
            jdbc.update("""
                INSERT INTO app.attendance_entry(tenant_id,attendance_entry_id,institution_id,attendance_session_id,
                 student_profile_id,enrollment_id,status,remarks,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,tenantId,UUID.randomUUID(),institutionId,sessionId,item.studentProfileId(),item.enrollmentId(),
                notHeld?"NOT_HELD":item.status(),item.remarks(),actor.userAccountId(),actor.userAccountId());
        }
        return jdbc.queryForMap("SELECT * FROM app.attendance_session WHERE tenant_id=? AND attendance_session_id=?",tenantId,sessionId);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> roster(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID sectionId){
        authorize(tenantId,actor,"ATTENDANCE_VIEW",institutionId,departmentForSection(tenantId,institutionId,sectionId));
        return jdbc.queryForList("""
            SELECT e.enrollment_id,p.student_profile_id,p.student_code,p.full_name
              FROM app.student_enrollment e JOIN app.student_profile p
                ON p.tenant_id=e.tenant_id AND p.institution_id=e.institution_id AND p.student_profile_id=e.student_profile_id
             WHERE e.tenant_id=? AND e.institution_id=? AND e.section_id=? AND e.outcome='IN_PROGRESS'
               AND e.ended_on IS NULL AND p.status='ACTIVE'
             ORDER BY p.full_name,p.student_code
            """,tenantId,institutionId,sectionId);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> register(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID sectionId,LocalDate date){
        authorize(tenantId,actor,"ATTENDANCE_VIEW",institutionId,departmentForSection(tenantId,institutionId,sectionId));
        return jdbc.queryForList("""
            SELECT s.attendance_session_id,s.session_date,s.session_type,s.session_status,s.timetable_slot_id,
                   e.attendance_entry_id,e.enrollment_id,e.student_profile_id,p.student_code,p.full_name,e.status,e.remarks
              FROM app.attendance_session s
              LEFT JOIN app.attendance_entry e ON e.tenant_id=s.tenant_id AND e.institution_id=s.institution_id
               AND e.attendance_session_id=s.attendance_session_id
              LEFT JOIN app.student_profile p ON p.tenant_id=e.tenant_id AND p.institution_id=e.institution_id
               AND p.student_profile_id=e.student_profile_id
             WHERE s.tenant_id=? AND s.institution_id=? AND s.section_id=? AND s.session_date=?
             ORDER BY p.full_name
            """,tenantId,institutionId,sectionId,date);
    }

    @Transactional
    public Map<String,Object> submit(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID sessionId){
        UUID sectionId=jdbc.query("""
            SELECT section_id FROM app.attendance_session WHERE tenant_id=? AND institution_id=? AND attendance_session_id=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,sessionId);
        if(sectionId==null)throw new ResourceNotFoundException("Attendance session not found.");
        authorize(tenantId,actor,"ATTENDANCE_MARK",institutionId,departmentForSection(tenantId,institutionId,sectionId));
        int updated=jdbc.update("""
            UPDATE app.attendance_session SET session_status='SUBMITTED',submitted_at=now(),updated_by=?
             WHERE tenant_id=? AND institution_id=? AND attendance_session_id=? AND session_status='HELD'
            """,actor.userAccountId(),tenantId,institutionId,sessionId);
        if(updated==0)throw new BusinessRuleViolationException("ATTENDANCE_ALREADY_SUBMITTED","Attendance session is missing, not held, or already submitted.");
        return jdbc.queryForMap("SELECT * FROM app.attendance_session WHERE tenant_id=? AND attendance_session_id=?",tenantId,sessionId);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> percentage(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID enrollmentId){
        authorize(tenantId,actor,"ATTENDANCE_VIEW",institutionId,departmentForEnrollment(tenantId,institutionId,enrollmentId));
        try{return calculator.calculate(tenantId,institutionId,enrollmentId);}
        catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Attendance record not found.");}
    }

    @Transactional(readOnly=true)
    public Map<String,Object> eligibility(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID enrollmentId){
        authorize(tenantId,actor,"ATTENDANCE_VIEW",institutionId,departmentForEnrollment(tenantId,institutionId,enrollmentId));
        try{return calculator.eligibility(tenantId,institutionId,enrollmentId);}
        catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Attendance eligibility record not found.");}
    }

    @Transactional(readOnly=true)
    public Map<String,Object> threshold(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID departmentId){
        authorize(tenantId,actor,"ATTENDANCE_VIEW",institutionId,departmentId);
        try{return jdbc.queryForMap("""
            SELECT * FROM app.attendance_threshold_policy
             WHERE tenant_id=? AND institution_id=? AND is_active
               AND (department_id IS NOT DISTINCT FROM ? OR department_id IS NULL)
             ORDER BY (department_id IS NOT NULL) DESC,effective_from DESC LIMIT 1
            """,tenantId,institutionId,departmentId);}
        catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Attendance threshold settings not found.");}
    }

    @Transactional
    public Map<String,Object> updateThreshold(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID departmentId,
            BigDecimal threshold,LocalDate effectiveFrom){
        authorize(tenantId,actor,"ATTENDANCE_RULES_SET",institutionId,departmentId);
        if(threshold==null||threshold.compareTo(BigDecimal.ZERO)<0||threshold.compareTo(new BigDecimal("100"))>0)
            throw new IllegalArgumentException("Attendance threshold must be between 0 and 100.");
        LocalDate from=effectiveFrom==null?LocalDate.now():effectiveFrom;
        if(from.isAfter(LocalDate.now())) throw new IllegalArgumentException("Threshold changes must take effect today or earlier.");
        jdbc.update("""
            UPDATE app.attendance_threshold_policy SET is_active=false,updated_by=?
             WHERE tenant_id=? AND institution_id=? AND department_id IS NOT DISTINCT FROM ? AND is_active
            """,actor.userAccountId(),tenantId,institutionId,departmentId);
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.attendance_threshold_policy(tenant_id,attendance_threshold_policy_id,institution_id,
             department_id,minimum_attendance_percentage,effective_from,is_active,created_by,updated_by)
            VALUES (?,?,?,?,?,?,true,?,?)
            """,tenantId,id,institutionId,departmentId,threshold,from,actor.userAccountId(),actor.userAccountId());
        return jdbc.queryForMap("SELECT * FROM app.attendance_threshold_policy WHERE tenant_id=? AND attendance_threshold_policy_id=?",tenantId,id);
    }

    private void authorize(UUID tenantId,TenantPrincipal actor,String action,UUID institutionId,UUID departmentId){
        RbacPrincipal principal=rbac(tenantId,actor);
        UserRoleAssignment active=permissions.activeAssignment(principal)
            .orElseThrow(()->new UnauthorizedException("An active role is required for attendance access."));
        RbacScope scope=switch(active.getScopeType()){
            case TENANT -> RbacScope.tenant();
            case INSTITUTION -> RbacScope.institution(institutionId);
            case DEPARTMENT -> {
                if(departmentId==null)throw new UnauthorizedException("This attendance operation requires a department-scoped target.");
                yield RbacScope.department(institutionId,departmentId);
            }
        };
        permissions.authorize(principal,action,scope,RbacTarget.NONE);
    }

    private UUID departmentForSection(UUID tenantId,UUID institutionId,UUID sectionId){
        return jdbc.query("""
            SELECT pr.department_id FROM app.section s
              LEFT JOIN app.semester sm ON sm.tenant_id=s.tenant_id AND sm.semester_id=s.semester_id
              LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id
              LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id
             WHERE s.tenant_id=? AND s.institution_id=? AND s.section_id=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,sectionId);
    }

    private UUID departmentForEnrollment(UUID tenantId,UUID institutionId,UUID enrollmentId){
        UUID sectionId=jdbc.query("""
            SELECT section_id FROM app.student_enrollment WHERE tenant_id=? AND institution_id=? AND enrollment_id=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,enrollmentId);
        return sectionId==null?null:departmentForSection(tenantId,institutionId,sectionId);
    }

    private static RbacPrincipal rbac(UUID tenantId,TenantPrincipal actor){return new RbacPrincipal(tenantId,actor.userAccountId());}
    public record AttendanceItem(UUID enrollmentId,UUID studentProfileId,String status,String remarks){}
}