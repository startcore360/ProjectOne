package com.edu360.api.academic.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.exception.UnauthorizedException;

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

    @Transactional(readOnly = true)
    public List<Map<String,Object>> listRooms(UUID tenantId,TenantPrincipal actor,UUID institutionId) {
        authorize(tenantId,actor,"TIMETABLE_VIEW",institutionId,null);
        return jdbc.queryForList("SELECT * FROM app.room WHERE tenant_id=? AND institution_id=? AND status='ACTIVE' ORDER BY building,room_number",tenantId,institutionId);
    }

    @Transactional
    public Map<String,Object> updateSlot(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID slotId,
            int day,String start,String end,UUID roomId) {
        UUID sectionId=jdbc.query("""
            SELECT section_id FROM app.timetable_slot WHERE tenant_id=? AND institution_id=? AND timetable_slot_id=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,slotId);
        if(sectionId==null)throw new com.edu360.api.exception.ResourceNotFoundException("Timetable slot not found.");
        authorize(tenantId,actor,"TIMETABLE_BUILD",institutionId,departmentOfSection(tenantId,institutionId,sectionId));
        int count=jdbc.update("""
            UPDATE app.timetable_slot SET day_of_week=?,start_time=?,end_time=?,room_id=?,updated_by=?
             WHERE tenant_id=? AND institution_id=? AND timetable_slot_id=? AND status='ACTIVE'
            """,day,start,end,roomId,actor.userAccountId(),tenantId,institutionId,slotId);
        if(count==0)throw new com.edu360.api.exception.ResourceNotFoundException("Timetable slot not found.");
        return jdbc.queryForMap("SELECT * FROM app.timetable_slot WHERE tenant_id=? AND timetable_slot_id=?",tenantId,slotId);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> list(UUID tenantId, TenantPrincipal actor, UUID institutionId,
            UUID sectionId, UUID academicSessionId) {
        authorize(tenantId,actor,"TIMETABLE_VIEW",institutionId,sectionId==null?null:departmentOfSection(tenantId,institutionId,sectionId));
        return jdbc.queryForList("""
            SELECT s.timetable_slot_id,s.institution_id,s.academic_session_id,s.section_id,
                   s.course_offering_id,s.teacher_id,s.room_id,r.room_number,s.day_of_week,
                   s.start_time,s.end_time,s.online_meeting_url,s.status,s.row_version
              FROM app.timetable_slot s LEFT JOIN app.room r
                ON r.tenant_id=s.tenant_id AND r.institution_id=s.institution_id AND r.room_id=s.room_id
             WHERE s.tenant_id=? AND s.institution_id=? AND (?::uuid IS NULL OR s.section_id=?)
               AND (?::uuid IS NULL OR s.academic_session_id=?)
             ORDER BY s.section_id,s.day_of_week,s.start_time
            """, tenantId,institutionId,sectionId,sectionId,academicSessionId,academicSessionId);
    }

    @Transactional
    public Map<String,Object> createSlot(UUID tenantId,TenantPrincipal actor,UUID institutionId,UUID sessionId,
            UUID sectionId,UUID offeringId,UUID teacherId,UUID roomId,int day,String start,String end,String meetingUrl) {
        authorize(tenantId,actor,"TIMETABLE_BUILD",institutionId,departmentOfSection(tenantId,institutionId,sectionId));
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.timetable_slot(tenant_id,timetable_slot_id,institution_id,academic_session_id,
              section_id,course_offering_id,teacher_id,room_id,day_of_week,start_time,end_time,online_meeting_url,
              created_by,updated_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?, ?,?,?)
            """,tenantId,id,institutionId,sessionId,sectionId,offeringId,teacherId,roomId,day,start,end,meetingUrl,
            actor.userAccountId(),actor.userAccountId());
        return jdbc.queryForMap("SELECT * FROM app.timetable_slot WHERE tenant_id=? AND timetable_slot_id=?",tenantId,id);
    }

    @Transactional
    public Map<String,Object> createOnlineSession(UUID tenantId,TenantPrincipal actor,UUID institutionId,
            UUID slotId,String provider,String meetingUrl,Instant expiresAt) {
        UUID sectionId=jdbc.query("""
            SELECT section_id FROM app.timetable_slot WHERE tenant_id=? AND institution_id=? AND timetable_slot_id=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,slotId);
        if(sectionId==null)throw new com.edu360.api.exception.ResourceNotFoundException("Timetable slot not found.");
        authorize(tenantId,actor,"ONLINE_PERIOD_SET",institutionId,departmentOfSection(tenantId,institutionId,sectionId));
        if (!List.of("ZOOM","GOOGLE_MEET","TEAMS","CUSTOM").contains(provider))
            throw new IllegalArgumentException("Unsupported online meeting provider.");
        if (expiresAt==null || !expiresAt.isAfter(Instant.now()))
            throw new IllegalArgumentException("Token expiry must be in the future.");
        UUID id=UUID.randomUUID();
        byte[] raw=new byte[32]; random.nextBytes(raw);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String hash=sha256(token);
        jdbc.update("""
            INSERT INTO app.online_session(tenant_id,online_session_id,institution_id,timetable_slot_id,
              provider,meeting_url,access_token_hash,token_expires_at,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,tenantId,id,institutionId,slotId,provider,meetingUrl,hash,expiresAt,actor.userAccountId(),actor.userAccountId());
        jdbc.update("""
            UPDATE app.timetable_slot SET online_meeting_url=?,updated_by=?
             WHERE tenant_id=? AND institution_id=? AND timetable_slot_id=?
            """,meetingUrl,actor.userAccountId(),tenantId,institutionId,slotId);
        return Map.of("onlineSessionId",id,"provider",provider,"meetingUrl",meetingUrl,"accessToken",token,
                "tokenExpiresAt",expiresAt);
    }

    @Transactional(noRollbackFor = BusinessRuleViolationException.class)
    public Map<String,Object> accessOnlineSession(UUID tenantId,TenantPrincipal actor,String token) {
        if (token==null || token.length()<32 || token.length()>256)
            throw new BusinessRuleViolationException("ONLINE_SESSION_ACCESS_DENIED","The online session token is invalid or expired.");
        String hash=sha256(token);
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT os.online_session_id,os.institution_id,os.provider,os.meeting_url,os.token_expires_at,os.status,
                   ts.section_id,sp.student_profile_id,
                   (os.status='ACTIVE' AND os.token_expires_at>now()) AS token_valid,
                   (e.enrollment_id IS NOT NULL) AS enrolled
              FROM app.online_session os JOIN app.timetable_slot ts
                ON ts.tenant_id=os.tenant_id AND ts.institution_id=os.institution_id AND ts.timetable_slot_id=os.timetable_slot_id
              LEFT JOIN app.student_profile sp ON sp.tenant_id=os.tenant_id AND sp.user_account_id=?
               AND sp.institution_id=os.institution_id AND sp.status='ACTIVE'
              LEFT JOIN app.student_enrollment e ON e.tenant_id=sp.tenant_id AND e.student_profile_id=sp.student_profile_id
               AND e.institution_id=sp.institution_id AND e.section_id=ts.section_id
               AND e.outcome='IN_PROGRESS' AND e.ended_on IS NULL
             WHERE os.tenant_id=? AND os.access_token_hash=?
            """,actor.userAccountId(),tenantId,hash);
        if(rows.isEmpty()) throw new BusinessRuleViolationException("ONLINE_SESSION_ACCESS_DENIED","The online session token is invalid.");
        Map<String,Object> row=rows.get(0);
        boolean allowed=Boolean.TRUE.equals(row.get("token_valid")) && Boolean.TRUE.equals(row.get("enrolled")) && row.get("student_profile_id")!=null;
        if(row.get("student_profile_id")!=null) jdbc.update("""
            INSERT INTO app.online_session_access_log(tenant_id,access_log_id,institution_id,online_session_id,
              student_profile_id,access_result) VALUES (?,?,?,?,?,?)
            """,tenantId,UUID.randomUUID(),row.get("institution_id"),row.get("online_session_id"),
            row.get("student_profile_id"),allowed?"ALLOWED":"DENIED");
        if(!allowed) throw new BusinessRuleViolationException("ONLINE_SESSION_ACCESS_DENIED","The online session token is revoked or expired.");
        return Map.of("meetingUrl",row.get("meeting_url"),"provider",row.get("provider"));
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

    private UUID departmentOfSection(UUID tenantId,UUID institutionId,UUID sectionId){
        return jdbc.query("""
            SELECT pr.department_id FROM app.section s
              LEFT JOIN app.semester sm ON sm.tenant_id=s.tenant_id AND sm.semester_id=s.semester_id
              LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id
              LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id
             WHERE s.tenant_id=? AND s.institution_id=? AND s.section_id=?
            """,rs->rs.next()?rs.getObject(1,UUID.class):null,tenantId,institutionId,sectionId);
    }

    private static RbacPrincipal rbac(UUID tenantId,TenantPrincipal actor){return new RbacPrincipal(tenantId,actor.userAccountId());}
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ex){throw new IllegalStateException("SHA-256 is unavailable.",ex);}
    }
}