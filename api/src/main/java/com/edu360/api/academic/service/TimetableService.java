package com.edu360.api.academic.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.UserRoleAssignment;

@Service
public class TimetableService {
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final SecureRandom random = new SecureRandom();

    public TimetableService(JdbcTemplate jdbc, PermissionEvaluatorService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @Transactional
    public Map<String,Object> createRoom(UUID tenantId,TenantPrincipal actor,UUID institutionId,String roomNumber,
            String building,int capacity,String roomType) {
        authorize(tenantId,actor,"TIMETABLE_BUILD",institutionId,null);
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.room(tenant_id,room_id,institution_id,room_number,building,capacity,room_type,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?,?,?)
            """,tenantId,id,institutionId,roomNumber,building,capacity,roomType,actor.userAccountId(),actor.userAccountId());
        return jdbc.queryForMap("SELECT * FROM app.room WHERE tenant_id=? AND room_id=?",tenantId,id);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> listRooms(UUID tenantId,TenantPrincipal actor,UUID institutionId) {
        authorize(tenantId,actor,"TIMETABLE_VIEW",institutionId,null);
        return jdbc.queryForList("""
            SELECT * FROM app.room WHERE tenant_id=? AND institution_id=? AND status='ACTIVE'
             ORDER BY building,room_number
            """,tenantId,institutionId);
    }

    @Transactional
    public Map<String,Object> createSlot(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID sectionId,
            UUID subjectGroupId,UUID subjectAllocationId,int periodNo,int dayOfWeek,String startTime,String endTime,
            UUID roomId,boolean isOnline,LocalDate validFrom,LocalDate validTo) {
        UUID academicSessionId=jdbc.query("""
            SELECT co.academic_session_id FROM app.subject_allocation sa
              JOIN app.course_offering co ON co.tenant_id=sa.tenant_id AND co.institution_id=sa.institution_id
               AND co.course_offering_id=sa.course_offering_id
             WHERE sa.tenant_id=? AND sa.institution_id=? AND sa.subject_allocation_id=? AND sa.status='ACTIVE'
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,subjectAllocationId);
        if(academicSessionId==null)throw new ResourceNotFoundException("Active subject allocation not found.");
        UUID departmentId=sectionId!=null?departmentForSection(tenantId,institutionId,sectionId)
            :departmentForAllocation(tenantId,institutionId,subjectAllocationId);
        authorize(tenantId,actor,"TIMETABLE_BUILD",institutionId,departmentId);
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.timetable_slot(tenant_id,timetable_slot_id,institution_id,academic_session_id,
             section_id,subject_group_id,subject_allocation_id,period_no,day_of_week,start_time,end_time,
             room_id,is_online,valid_from,valid_to,status,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?)
            """,tenantId,id,institutionId,academicSessionId,sectionId,subjectGroupId,subjectAllocationId,
            periodNo,dayOfWeek,LocalTime.parse(startTime),LocalTime.parse(endTime),roomId,isOnline,validFrom,validTo,
            actor.userAccountId(),actor.userAccountId());
        return slot(tenantId,id);
    }

    @Transactional
    public Map<String,Object> updateSlot(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID slotId,
            int dayOfWeek,int periodNo,String startTime,String endTime,UUID roomId) {
        Map<String,Object> existing;
        try{existing=jdbc.queryForMap("""
            SELECT section_id,subject_group_id,subject_allocation_id,status FROM app.timetable_slot
             WHERE tenant_id=? AND institution_id=? AND timetable_slot_id=?
            """,tenantId,institutionId,slotId);}
        catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Timetable slot not found.");}
        UUID sectionId=(UUID)existing.get("section_id");
        UUID allocationId=(UUID)existing.get("subject_allocation_id");
        UUID departmentId=sectionId!=null?departmentForSection(tenantId,institutionId,sectionId)
            :departmentForAllocation(tenantId,institutionId,allocationId);
        authorize(tenantId,actor,"TIMETABLE_BUILD",institutionId,departmentId);
        if(!"DRAFT".equals(existing.get("status")))throw new BusinessRuleViolationException("PUBLISHED_SLOT_IMMUTABLE","Only draft timetable slots can be moved. Supersede and republish a published slot.");
        int updated=jdbc.update("""
            UPDATE app.timetable_slot SET day_of_week=?,period_no=?,start_time=?,end_time=?,room_id=?,updated_by=?
             WHERE tenant_id=? AND institution_id=? AND timetable_slot_id=? AND status='DRAFT'
            """,dayOfWeek,periodNo,LocalTime.parse(startTime),LocalTime.parse(endTime),roomId,
            actor.userAccountId(),tenantId,institutionId,slotId);
        if(updated==0)throw new ResourceNotFoundException("Draft timetable slot not found.");
        return slot(tenantId,slotId);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID sectionId,
            UUID subjectGroupId,UUID academicSessionId) {
        UUID departmentId=sectionId!=null?departmentForSection(tenantId,institutionId,sectionId)
            :subjectGroupId!=null?departmentForGroup(tenantId,institutionId,subjectGroupId):null;
        authorize(tenantId,actor,"TIMETABLE_VIEW",institutionId,departmentId);
        return jdbc.queryForList("""
            SELECT ts.timetable_slot_id,ts.institution_id,ts.academic_session_id,ts.section_id,ts.subject_group_id,
                   ts.subject_allocation_id,ts.period_no,ts.teacher_id,sp.full_name AS teacher_name,
                   sa.course_offering_id,ts.room_id,r.room_number,r.building,ts.day_of_week,ts.start_time,ts.end_time,
                   ts.is_online,ts.valid_from,ts.valid_to,ts.status,ts.row_version,
                   ml.meeting_link_id,ml.provider,ml.join_url,ml.occurrence_date,ml.status AS meeting_status
              FROM app.timetable_slot ts
              JOIN app.subject_allocation sa ON sa.tenant_id=ts.tenant_id AND sa.institution_id=ts.institution_id
               AND sa.subject_allocation_id=ts.subject_allocation_id
              LEFT JOIN app.staff_profile sp ON sp.tenant_id=ts.tenant_id AND sp.staff_profile_id=ts.teacher_id
              LEFT JOIN app.room r ON r.tenant_id=ts.tenant_id AND r.institution_id=ts.institution_id AND r.room_id=ts.room_id
              LEFT JOIN LATERAL (
                SELECT m.meeting_link_id,m.provider,m.join_url,m.occurrence_date,m.status
                  FROM app.meeting_link m WHERE m.tenant_id=ts.tenant_id AND m.institution_id=ts.institution_id
                   AND m.timetable_slot_id=ts.timetable_slot_id ORDER BY m.occurrence_date DESC LIMIT 1
              ) ml ON true
             WHERE ts.tenant_id=? AND ts.institution_id=?
               AND (?::uuid IS NULL OR ts.section_id=?)
               AND (?::uuid IS NULL OR ts.subject_group_id=?)
               AND (?::uuid IS NULL OR ts.academic_session_id=?)
               AND ts.status IN ('DRAFT','PUBLISHED')
             ORDER BY ts.day_of_week,ts.start_time,ts.period_no
            """,tenantId,institutionId,sectionId,sectionId,subjectGroupId,subjectGroupId,academicSessionId,academicSessionId);
    }

    @Transactional
    public Map<String,Object> createMeetingLink(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID slotId,
            LocalDate occurrenceDate,String provider,String joinUrl,String externalMeetingId,Instant expiresAt) {
        Map<String,Object> existing;
        try{existing=jdbc.queryForMap("""
            SELECT section_id,subject_group_id,subject_allocation_id,academic_session_id,valid_from,valid_to,status
              FROM app.timetable_slot WHERE tenant_id=? AND institution_id=? AND timetable_slot_id=?
            """,tenantId,institutionId,slotId);}
        catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Timetable slot not found.");}
        UUID sectionId=(UUID)existing.get("section_id");
        UUID allocationId=(UUID)existing.get("subject_allocation_id");
        UUID departmentId=sectionId!=null?departmentForSection(tenantId,institutionId,sectionId)
            :departmentForAllocation(tenantId,institutionId,allocationId);
        authorize(tenantId,actor,"ONLINE_PERIOD_SET",institutionId,departmentId);
        if(!List.of("DRAFT","PUBLISHED").contains(existing.get("status")))throw new BusinessRuleViolationException("TIMETABLE_SLOT_NOT_AVAILABLE","A superseded timetable slot cannot receive a meeting link.");
        if(occurrenceDate.isBefore(asLocalDate(existing.get("valid_from")))
          || (existing.get("valid_to")!=null && occurrenceDate.isAfter(asLocalDate(existing.get("valid_to")))))
            throw new BusinessRuleViolationException("MEETING_DATE_OUTSIDE_SLOT","Meeting date is outside the timetable slot validity.");
        if(!expiresAt.isAfter(Instant.now()))throw new BusinessRuleViolationException("MEETING_TOKEN_EXPIRY_INVALID","Meeting access token expiry must be in the future.");
        byte[] tokenBytes=new byte[32];random.nextBytes(tokenBytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash=sha256(token);
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.meeting_link(tenant_id,meeting_link_id,institution_id,timetable_slot_id,occurrence_date,
             provider,join_url,external_meeting_id,status,access_token_hash,token_expires_at,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?,?,'CREATED',?,?,?,?)
            """,tenantId,id,institutionId,slotId,occurrenceDate,provider,joinUrl,externalMeetingId,
            tokenHash,java.sql.Timestamp.from(expiresAt),actor.userAccountId(),actor.userAccountId());
        jdbc.update("UPDATE app.timetable_slot SET is_online=true,updated_by=? WHERE tenant_id=? AND institution_id=? AND timetable_slot_id=?",
            actor.userAccountId(),tenantId,institutionId,slotId);
        return Map.of("meetingLinkId",id,"accessToken",token,"tokenExpiresAt",expiresAt.toString(),"provider",provider);
    }

    @Transactional(noRollbackFor=BusinessRuleViolationException.class)
    public Map<String,Object> accessOnlineMeeting(UUID tenantId,TenantPrincipal actor,String accessToken) {
        String tokenHash=sha256(accessToken);
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT ml.meeting_link_id,ml.institution_id,ml.provider,ml.join_url,ml.occurrence_date,
                   (ml.status='CREATED' AND ml.token_expires_at>now()) AS token_valid,
                   sp.student_profile_id,
                   CASE WHEN sp.student_profile_id IS NULL THEN false ELSE EXISTS(
                    SELECT 1 FROM app.student_enrollment e
                     WHERE e.tenant_id=ml.tenant_id AND e.institution_id=ml.institution_id
                      AND e.student_profile_id=sp.student_profile_id AND e.outcome='IN_PROGRESS' AND e.ended_on IS NULL
                      AND ((ts.section_id IS NOT NULL AND e.section_id=ts.section_id)
                       OR (ts.subject_group_id IS NOT NULL AND EXISTS(
                        SELECT 1 FROM app.subject_group_member gm WHERE gm.tenant_id=e.tenant_id
                         AND gm.institution_id=e.institution_id AND gm.enrollment_id=e.enrollment_id
                         AND gm.subject_group_id=ts.subject_group_id)))
                   ) END AS enrolled
              FROM app.meeting_link ml
              JOIN app.timetable_slot ts ON ts.tenant_id=ml.tenant_id AND ts.institution_id=ml.institution_id
               AND ts.timetable_slot_id=ml.timetable_slot_id
              LEFT JOIN app.student_profile sp ON sp.tenant_id=ml.tenant_id AND sp.institution_id=ml.institution_id
               AND sp.user_account_id=? AND sp.status='ACTIVE'
             WHERE ml.tenant_id=? AND ml.access_token_hash=?
            """,actor.userAccountId(),tenantId,tokenHash);
        if(rows.isEmpty())throw new BusinessRuleViolationException("ONLINE_SESSION_ACCESS_DENIED","The online meeting token is invalid.");
        Map<String,Object> row=rows.get(0);
        boolean allowed=Boolean.TRUE.equals(row.get("token_valid"))&&Boolean.TRUE.equals(row.get("enrolled"))&&row.get("student_profile_id")!=null;
        if(row.get("student_profile_id")!=null)jdbc.update("""
            INSERT INTO app.online_session_access_log(tenant_id,access_log_id,institution_id,meeting_link_id,
             student_profile_id,access_result) VALUES (?,?,?,?,?,?)
            """,tenantId,UUID.randomUUID(),row.get("institution_id"),row.get("meeting_link_id"),
            row.get("student_profile_id"),allowed?"ALLOWED":"DENIED");
        if(!allowed)throw new BusinessRuleViolationException("ONLINE_SESSION_ACCESS_DENIED","The online meeting is expired, cancelled or the student is not enrolled.");
        return Map.of("meetingUrl",row.get("join_url"),"provider",row.get("provider"),"occurrenceDate",row.get("occurrence_date").toString());
    }

    private Map<String,Object> slot(UUID tenantId,UUID slotId){
        try{return jdbc.queryForMap("""
            SELECT ts.*,sp.full_name AS teacher_name,r.room_number,r.building
              FROM app.timetable_slot ts
              LEFT JOIN app.staff_profile sp ON sp.tenant_id=ts.tenant_id AND sp.staff_profile_id=ts.teacher_id
              LEFT JOIN app.room r ON r.tenant_id=ts.tenant_id AND r.institution_id=ts.institution_id AND r.room_id=ts.room_id
             WHERE ts.tenant_id=? AND ts.timetable_slot_id=?
            """,tenantId,slotId);}
        catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Timetable slot not found.");}
    }

    private void authorize(UUID tenantId,TenantPrincipal actor,String action,UUID institutionId,UUID departmentId){
        RbacPrincipal principal=rbac(tenantId,actor);
        UserRoleAssignment active=permissions.activeAssignment(principal)
            .orElseThrow(()->new UnauthorizedException("An active role is required for timetable access."));
        RbacScope scope=switch(active.getScopeType()){
            case TENANT -> RbacScope.tenant();
            case INSTITUTION -> RbacScope.institution(institutionId);
            case DEPARTMENT -> {
                if(departmentId==null)throw new UnauthorizedException("This timetable operation requires a department-scoped target.");
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

    private UUID departmentForAllocation(UUID tenantId,UUID institutionId,UUID allocationId){
        return jdbc.query("""
            SELECT pr.department_id FROM app.subject_allocation sa
              JOIN app.course_offering co ON co.tenant_id=sa.tenant_id AND co.institution_id=sa.institution_id
               AND co.course_offering_id=sa.course_offering_id
              LEFT JOIN app.semester sm ON sm.tenant_id=co.tenant_id AND sm.semester_id=co.semester_id
              LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id
              LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id
             WHERE sa.tenant_id=? AND sa.institution_id=? AND sa.subject_allocation_id=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,allocationId);
    }

    private UUID departmentForGroup(UUID tenantId,UUID institutionId,UUID groupId){
        return jdbc.query("""
            SELECT pr.department_id FROM app.subject_group g
              JOIN app.course_offering co ON co.tenant_id=g.tenant_id AND co.institution_id=g.institution_id AND co.course_offering_id=g.course_offering_id
              LEFT JOIN app.semester sm ON sm.tenant_id=co.tenant_id AND sm.semester_id=co.semester_id
              LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id
              LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id
             WHERE g.tenant_id=? AND g.institution_id=? AND g.subject_group_id=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,groupId);
    }

    private static LocalDate asLocalDate(Object value){
        if(value==null)return null;
        if(value instanceof LocalDate date)return date;
        if(value instanceof java.sql.Date date)return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private static String sha256(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException("SHA-256 is unavailable.",ex);}
    }
    private static RbacPrincipal rbac(UUID tenantId,TenantPrincipal actor){return new RbacPrincipal(tenantId,actor.userAccountId());}
}