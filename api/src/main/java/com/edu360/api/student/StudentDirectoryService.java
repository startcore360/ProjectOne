package com.edu360.api.student;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentDirectoryService {
    private static final int PAGE_SIZE = 50;
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;

    public StudentDirectoryService(NamedParameterJdbcTemplate jdbc, PermissionEvaluatorService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public StudentDirectoryResponse list(UUID tenantId, TenantPrincipal actor, UUID institutionId,
            UUID departmentId, UUID programId, UUID batchId, UUID sectionId, String status, String q, String cursor) {
        authorize(tenantId, actor, "STUDENT_SEARCH", institutionId);
        Cursor after = decode(cursor);
        StringBuilder sql = new StringBuilder("""
            SELECT s.student_profile_id AS "studentProfileId", s.student_code AS "studentCode",
                   s.admission_id AS "admissionId", s.full_name AS "fullName", s.preferred_name AS "preferredName",
                   s.status AS "status", s.institution_id AS "institutionId", s.department_id AS "departmentId",
                   s.program_id AS "programId", s.batch_id AS "batchId", e.section_id AS "sectionId",
                   e.roll_number AS "rollNumber", s.photo_file_id AS "photoFileId", s.updated_at AS "updatedAt"
              FROM app.student_profile s
              LEFT JOIN LATERAL (SELECT se.section_id, se.roll_number FROM app.student_enrollment se
                WHERE se.tenant_id=s.tenant_id AND se.student_profile_id=s.student_profile_id
                ORDER BY se.started_on DESC LIMIT 1) e ON true
             WHERE s.tenant_id=:tenantId
            """);
        MapSqlParameterSource p = new MapSqlParameterSource("tenantId", tenantId);
        add(sql,p,"s.institution_id","institutionId",institutionId);
        add(sql,p,"s.department_id","departmentId",departmentId);
        add(sql,p,"s.program_id","programId",programId);
        add(sql,p,"s.batch_id","batchId",batchId);
        add(sql,p,"e.section_id","sectionId",sectionId);
        if (status != null && !status.isBlank()) { sql.append(" AND s.status=:status"); p.addValue("status", status.toUpperCase()); }
        if (q != null && !q.isBlank()) { sql.append(" AND (s.student_code ILIKE :q OR s.full_name ILIKE :q OR s.mobile ILIKE :q OR s.admission_id ILIKE :q)"); p.addValue("q", "%"+q.trim()+"%"); }
        if (after != null) {
            sql.append(" AND (s.student_code, s.student_profile_id) > (:afterCode, :afterId)");
            p.addValue("afterCode", after.code()).addValue("afterId", after.id());
        }
        sql.append(" ORDER BY s.student_code, s.student_profile_id LIMIT :limit");
        p.addValue("limit", PAGE_SIZE + 1);
        List<Map<String,Object>> rows = jdbc.queryForList(sql.toString(),p);
        boolean more = rows.size() > PAGE_SIZE;
        if (more) rows = rows.subList(0,PAGE_SIZE);
        String next = more && !rows.isEmpty() ? encode(String.valueOf(rows.get(rows.size()-1).get("studentCode")),
                (UUID) rows.get(rows.size()-1).get("studentProfileId")) : null;
        return new StudentDirectoryResponse(rows,next,more);
    }

    @Transactional(readOnly = true)
    public Map<String,Object> get(UUID tenantId, TenantPrincipal actor, UUID studentId) {
        Map<String,Object> row = jdbc.queryForMap("""
            SELECT s.student_profile_id, s.student_code, s.full_name, s.date_of_birth, s.admission_date,
                   s.admission_id, s.gender, s.preferred_name, s.email, s.mobile,
                   s.address_line1, s.address_line2, s.city, s.state_region, s.postal_code, s.country_code,
                   s.status, s.status_reason, s.status_changed_at, s.institution_id, s.department_id,
                   s.program_id, s.batch_id, s.photo_file_id, s.updated_at
              FROM app.student_profile s WHERE s.tenant_id=:tenantId AND s.student_profile_id=:studentId
            """, Map.of("tenantId",tenantId,"studentId",studentId));
        authorize(tenantId,actor,"STUDENT_SEARCH",(UUID)row.get("institution_id"));
        return row;
    }

    @Transactional
    public Map<String,Object> update(UUID tenantId, TenantPrincipal actor, UUID studentId, StudentProfileUpdateRequest r) {
        Map<String,Object> current = get(tenantId,actor,studentId);
        authorize(tenantId,actor,"STUDENT_RECORD_EDIT",(UUID)current.get("institution_id"));
        if (r.status() != null) throw new BusinessRuleViolationException("STATUS_CHANGE_REQUIRED", "Use the dedicated student status-change action.");
        String status = (String)current.get("status");
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId",tenantId).addValue("studentId",studentId)
          .addValue("actor",actor.userAccountId()).addValue("fullName",r.fullName()).addValue("dateOfBirth",date(r.dateOfBirth()))
          .addValue("admissionDate",date(r.admissionDate())).addValue("admissionId",r.admissionId()).addValue("gender",r.gender())
          .addValue("preferredName",r.preferredName()).addValue("email",r.email()).addValue("mobile",r.mobile())
          .addValue("addressLine1",r.addressLine1()).addValue("addressLine2",r.addressLine2()).addValue("city",r.city())
          .addValue("stateRegion",r.stateRegion()).addValue("postalCode",r.postalCode()).addValue("countryCode",r.countryCode())
          .addValue("departmentId",uuid(r.departmentId())).addValue("programId",uuid(r.programId())).addValue("batchId",uuid(r.batchId()))
          .addValue("status",status).addValue("statusReason",r.statusReason());
        jdbc.update("""
          UPDATE app.student_profile SET
            full_name=COALESCE(:fullName,full_name), date_of_birth=COALESCE(:dateOfBirth,date_of_birth),
            admission_date=COALESCE(:admissionDate,admission_date), admission_id=COALESCE(:admissionId,admission_id),
            gender=COALESCE(:gender,gender), preferred_name=COALESCE(:preferredName,preferred_name),
            email=COALESCE(:email,email), mobile=COALESCE(:mobile,mobile),
            address_line1=COALESCE(:addressLine1,address_line1), address_line2=COALESCE(:addressLine2,address_line2),
            city=COALESCE(:city,city), state_region=COALESCE(:stateRegion,state_region),
            postal_code=COALESCE(:postalCode,postal_code), country_code=COALESCE(:countryCode,country_code),
            department_id=COALESCE(:departmentId,department_id), program_id=COALESCE(:programId,program_id),
            batch_id=COALESCE(:batchId,batch_id), status=:status,
            status_reason=CASE WHEN :status <> status THEN :statusReason ELSE COALESCE(:statusReason,status_reason) END,
            status_changed_at=CASE WHEN :status <> status THEN now() ELSE status_changed_at END,
            updated_by=:actor
          WHERE tenant_id=:tenantId AND student_profile_id=:studentId
          """,p);
        return get(tenantId,actor,studentId);
    }

    @Transactional
    public Map<String,Object> addNote(UUID tenantId,TenantPrincipal actor,UUID studentId,StudentNoteRequest r) {
        Map<String,Object> student=get(tenantId,actor,studentId);
        authorize(tenantId,actor,"NOTE_ADD",(UUID)student.get("institution_id"));
        String category=r.category().toUpperCase();
        if (!List.of("ACADEMIC","BEHAVIORAL","MEDICAL","COUNSELING","DISCIPLINE","HEALTH").contains(category))
            throw new BusinessRuleViolationException("INVALID_NOTE_CATEGORY","Unsupported note category.");
        UUID noteId=UUID.randomUUID();
        jdbc.update("""
          INSERT INTO app.student_note(tenant_id,student_note_id,student_profile_id,institution_id,note_type,body,
            added_by_user_id,added_active_role,corrects_note_id,delete_after,created_by,updated_by)
          VALUES(:tenantId,:noteId,:studentId,:institutionId,:category,:body,:actor,:activeRole,:correctsNoteId,:deleteAfter,:actor,:actor)
          """,new MapSqlParameterSource().addValue("tenantId",tenantId).addValue("noteId",noteId).addValue("studentId",studentId)
            .addValue("institutionId",student.get("institution_id")).addValue("category",category).addValue("body",r.body())
            .addValue("actor",actor.userAccountId()).addValue("activeRole",activeRole(tenantId, actor.userAccountId()))
            .addValue("correctsNoteId",uuid(r.correctsNoteId())).addValue("deleteAfter",date(r.deleteAfter())));
        return jdbc.queryForMap("SELECT * FROM app.student_note WHERE tenant_id=:tenantId AND student_note_id=:noteId",
          Map.of("tenantId",tenantId,"noteId",noteId));
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> notes(UUID tenantId,TenantPrincipal actor,UUID studentId) {
        Map<String,Object> student=get(tenantId,actor,studentId);
        RbacScope scope = scope((UUID) student.get("institution_id"));
        boolean discipline = permissions.can(new RbacPrincipal(tenantId,actor.userAccountId()),"NOTE_VIEW_DISCIPLINE",scope,RbacTarget.NONE).allowed();
        boolean health = permissions.can(new RbacPrincipal(tenantId,actor.userAccountId()),"NOTE_VIEW_HEALTH",scope,RbacTarget.NONE).allowed();
        if (!discipline && !health) throw new com.edu360.api.exception.UnauthorizedException("Student note view permission required.");
        List<Map<String,Object>> all = jdbc.queryForList("SELECT * FROM app.student_note WHERE tenant_id=:tenantId AND student_profile_id=:studentId ORDER BY created_at DESC, student_note_id DESC",
          Map.of("tenantId",tenantId,"studentId",studentId));
        return all.stream().filter(n -> {
          String t=String.valueOf(n.get("note_type"));
          boolean sensitive=List.of("HEALTH","MEDICAL","COUNSELING").contains(t);
          return sensitive ? health : discipline;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> legalHolds(UUID tenantId,TenantPrincipal actor,UUID studentId) {
        Map<String,Object> student=get(tenantId,actor,studentId);
        return jdbc.queryForList("""
          SELECT legal_hold_id AS "legalHoldId",reason,placed_at AS "placedAt",released_at AS "releasedAt",
                 (released_at IS NULL) AS active FROM app.legal_hold
           WHERE tenant_id=:tenantId AND subject_kind='STUDENT' AND subject_id=:studentId
           ORDER BY placed_at DESC
          """,Map.of("tenantId",tenantId,"studentId",studentId));
    }

    private void authorize(UUID tenantId,TenantPrincipal actor,String action,UUID institutionId) {
        permissions.authorize(new RbacPrincipal(tenantId,actor.userAccountId()),action,scope(institutionId),RbacTarget.NONE);
    }
    private static RbacScope scope(UUID institutionId) {
        return institutionId == null ? RbacScope.tenant() : RbacScope.institution(institutionId);
    }
    private String activeRole(UUID tenantId, UUID userId) {
        List<Map<String, Object>> assignments = jdbc.queryForList("""
            SELECT ura.user_role_assignment_id AS assignment_id, rd.role_code
              FROM app.user_role_assignment ura
              JOIN app.role_definition rd
                ON rd.tenant_id=ura.tenant_id AND rd.role_definition_id=ura.role_definition_id
             WHERE ura.tenant_id=:tenantId AND ura.user_account_id=:userId
               AND ura.status='ACTIVE' AND ura.effective_from<=now()
               AND (ura.effective_to IS NULL OR ura.effective_to>now()) AND rd.status='ACTIVE'
             ORDER BY ura.user_role_assignment_id
            """, Map.of("tenantId", tenantId, "userId", userId));
        if (assignments.isEmpty()) {
            throw new BusinessRuleViolationException("ACTIVE_ROLE_REQUIRED", "An active role is required before adding a student note.");
        }
        UUID selected = jdbc.query("""
            SELECT user_role_assignment_id FROM app.user_active_role
             WHERE tenant_id=:tenantId AND user_account_id=:userId
            """, Map.of("tenantId", tenantId, "userId", userId), rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (selected != null) {
            return assignments.stream()
                .filter(a -> selected.equals(a.get("assignment_id")))
                .map(a -> String.valueOf(a.get("role_code")))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException("ACTIVE_ROLE_REQUIRED", "The selected active role is no longer valid."));
        }
        if (assignments.size() == 1) return String.valueOf(assignments.get(0).get("role_code"));
        throw new BusinessRuleViolationException("ACTIVE_ROLE_REQUIRED", "Select one active role before adding a student note.");
    }
    private static void add(StringBuilder s,MapSqlParameterSource p,String column,String name,UUID value){if(value!=null){s.append(" AND ").append(column).append("=:").append(name);p.addValue(name,value);}}
    private static LocalDate date(String v){return v==null||v.isBlank()?null:LocalDate.parse(v);}
    private static UUID uuid(String v){return v==null||v.isBlank()?null:UUID.fromString(v);}
    private static String encode(String code,UUID id){return Base64.getUrlEncoder().withoutPadding().encodeToString((code+"|"+id).getBytes(StandardCharsets.UTF_8));}
    private static Cursor decode(String token){if(token==null||token.isBlank())return null;try{String[] p=new String(Base64.getUrlDecoder().decode(token),StandardCharsets.UTF_8).split("\\|",2);return new Cursor(p[0],UUID.fromString(p[1]));}catch(Exception e){throw new BusinessRuleViolationException("INVALID_CURSOR","The directory cursor is invalid.");}}
    private record Cursor(String code,UUID id){}
}