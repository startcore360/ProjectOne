package com.edu360.api.admissions.service;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.StudentCreationService;
import com.edu360.api.identity.StudentProfileResponse;
import com.edu360.api.identity.StudentProfileOnlyRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class AdmissionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PermissionEvaluatorService permissions;
    private final StudentCreationService students;

    public AdmissionService(JdbcTemplate jdbc, ObjectMapper json, PermissionEvaluatorService permissions,
            StudentCreationService students) {
        this.jdbc=jdbc; this.json=json; this.permissions=permissions; this.students=students;
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> cycles(TenantPrincipal actor, UUID institutionId) {
        authorizeAny(actor,institutionId,"APPLICATION_SUBMIT","STRUCTURE_MANAGE");
        return jdbc.queryForList("select * from app.admission_cycle where tenant_id=? and (? is null or institution_id=?) order by start_date desc",
                actor.tenantId(),institutionId,institutionId);
    }

    @Transactional
    public Map<String,Object> createCycle(TenantPrincipal actor, Map<String,Object> in) {
        UUID institution=uuid(in,"institutionId"), session=uuid(in,"academicYearId"), program=uuid(in,"programId");
        authorize(actor,"STRUCTURE_MANAGE",institution);
        String name=required(in,"cycleName");
        LocalDate start=LocalDate.parse(required(in,"startDate")), end=LocalDate.parse(required(in,"endDate"));
        if(end.isBefore(start)) throw new BusinessRuleViolationException("VALIDATION_FAILED","endDate must not precede startDate.");
        int seats=integer(in,"totalSeats");
        if(seats<1) throw new BusinessRuleViolationException("VALIDATION_FAILED","totalSeats must be positive.");
        BigDecimal fee=decimal(in.getOrDefault("applicationFee",0));
        if(fee.signum()<0) throw new BusinessRuleViolationException("VALIDATION_FAILED","Application fee cannot be negative.");
        Integer sessionMatches=jdbc.queryForObject("select count(*) from app.academic_session where tenant_id=? and academic_session_id=? and (institution_id is null or institution_id=?)",
                Integer.class,actor.tenantId(),session,institution);
        Integer programMatches=jdbc.queryForObject("select count(*) from app.program where tenant_id=? and program_id=? and institution_id=?",
                Integer.class,actor.tenantId(),program,institution);
        if(sessionMatches==null||sessionMatches!=1||programMatches==null||programMatches!=1)
            throw new BusinessRuleViolationException("ADMISSION_CYCLE_REFERENCE_MISMATCH","Academic session and program must belong to the selected institution.");
        UUID id=UUID.randomUUID();
        Object quotaValue=in.getOrDefault("quotaCapacity",Map.of("GENERAL",seats,"MERIT",0,"SPORTS",0,"MANAGEMENT",0));
        Map<String,Object> quotaMap=readJsonObject(quotaValue);
        int quotaSum=0;
        for(String q:List.of("GENERAL","MERIT","SPORTS","MANAGEMENT")) {
            int cap=((Number)quotaMap.getOrDefault(q,0)).intValue();
            if(cap<0) throw new BusinessRuleViolationException("VALIDATION_FAILED","Quota capacity cannot be negative.");
            quotaSum+=cap;
        }
        if(quotaSum>seats) throw new BusinessRuleViolationException("VALIDATION_FAILED","Quota capacities cannot exceed total seats.");
        String quota=serialize(quotaValue);
        jdbc.update("""
          insert into app.admission_cycle(tenant_id,id,institution_id,academic_year_id,program_id,cycle_name,start_date,end_date,
          application_fee,total_seats,quota_capacity,status,created_by,updated_by)
          values(?,?,?,?,?,?,?,?,?,?,?::jsonb,'DRAFT',?,?)
          """,actor.tenantId(),id,institution,session,program,name,start,end,fee,seats,quota,actor.userAccountId(),actor.userAccountId());
        return one("select * from app.admission_cycle where tenant_id=? and id=?",actor.tenantId(),id);
    }

    @Transactional
    public Map<String,Object> updateCycle(TenantPrincipal actor, UUID id, Map<String,Object> in) {
        Map<String,Object> old=one("select * from app.admission_cycle where tenant_id=? and id=?",actor.tenantId(),id);
        UUID institution=(UUID)old.get("institution_id"); authorize(actor,"STRUCTURE_MANAGE",institution);
        String status=String.valueOf(in.getOrDefault("status",old.get("status")));
        if(!Set.of("DRAFT","OPEN","CLOSED","ARCHIVED").contains(status)) throw new BusinessRuleViolationException("VALIDATION_FAILED","Invalid cycle status.");
        jdbc.update("update app.admission_cycle set status=?,updated_by=? where tenant_id=? and id=?",
                status,actor.userAccountId(),actor.tenantId(),id);
        return one("select * from app.admission_cycle where tenant_id=? and id=?",actor.tenantId(),id);
    }

    @Transactional
    public Map<String,Object> submitApplication(TenantPrincipal actor, UUID cycleId, Map<String,Object> in) {
        Map<String,Object> cycle=one("select * from app.admission_cycle where tenant_id=? and id=?",actor.tenantId(),cycleId);
        if(!"OPEN".equals(cycle.get("status"))) throw new BusinessRuleViolationException("ADMISSION_CYCLE_CLOSED","This admission cycle is not open.");
        LocalDate today=LocalDate.now();
        if(today.isBefore((LocalDate)cycle.get("start_date"))||today.isAfter((LocalDate)cycle.get("end_date")))
            throw new BusinessRuleViolationException("APPLICATION_WINDOW_CLOSED","Applications are outside the published date window.");
        authorize(actor,"APPLICATION_SUBMIT",(UUID)cycle.get("institution_id"));
        String number="ADM-"+LocalDate.now().getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        UUID id=UUID.randomUUID();
        jdbc.update("""
          insert into app.admission_application(tenant_id,id,institution_id,cycle_id,application_number,applicant_name,email,phone,
          date_of_birth,qualifying_marks_percent,category,created_by,updated_by)
          values(?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,actor.tenantId(),id,cycle.get("institution_id"),cycleId,number,required(in,"applicantName"),
                required(in,"email"),required(in,"phone"),date(in.get("dateOfBirth")),decimal(in.get("qualifyingMarksPercent")),
                String.valueOf(in.getOrDefault("category","GENERAL")).toUpperCase(Locale.ROOT),actor.userAccountId(),actor.userAccountId());
        return one("select * from app.admission_application where tenant_id=? and id=?",actor.tenantId(),id);
    }

    @Transactional
    public Map<String,Object> attachDocument(TenantPrincipal actor, UUID applicationId, UUID fileId, String documentType) {
        Map<String,Object> app=ownedApplication(actor,applicationId);
        authorize(actor,"APPLICATION_SUBMIT",(UUID)app.get("institution_id"));
        if(!Set.of("PHOTO","IDENTITY","QUALIFICATION","SUPPORTING_DOCUMENT","APPLICATION_FEE_RECEIPT","ADMISSION_FEE_RECEIPT").contains(documentType))
            throw new BusinessRuleViolationException("VALIDATION_FAILED","Unsupported admission document type.");
        Integer clean=jdbc.queryForObject("select count(*) from app.file_object where tenant_id=? and file_id=? and institution_id=? and purpose='ADMISSION_DOCUMENT' and status='ACTIVE' and scan_status='CLEAN'",
                Integer.class,actor.tenantId(),fileId,app.get("institution_id"));
        if(clean==null||clean!=1) throw new BusinessRuleViolationException("ADMISSION_DOCUMENT_NOT_CLEAN","Admission documents must pass ClamAV scanning before attachment.");
        List<Map<String,Object>> docs;
        try { docs=json.readValue(String.valueOf(app.get("documents_json")),new TypeReference<>(){}); }
        catch(Exception ex){ docs=new ArrayList<>(); }
        docs.add(Map.of("fileId",fileId.toString(),"documentType",documentType,"uploadedAt",java.time.Instant.now().toString()));
        String receiptColumn="APPLICATION_FEE_RECEIPT".equals(documentType)?"application_fee_receipt_file_id":
                "ADMISSION_FEE_RECEIPT".equals(documentType)?"admission_fee_receipt_file_id":null;
        if(receiptColumn==null) jdbc.update("update app.admission_application set documents_json=?::jsonb,updated_by=? where tenant_id=? and id=?",
                serialize(docs),actor.userAccountId(),actor.tenantId(),applicationId);
        else jdbc.update("update app.admission_application set documents_json=?::jsonb,"+receiptColumn+"=?,updated_by=? where tenant_id=? and id=?",
                serialize(docs),fileId,actor.userAccountId(),actor.tenantId(),applicationId);
        return application(actor,applicationId);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> applicationForApplicant(TenantPrincipal actor,UUID applicationId) { return ownedApplication(actor,applicationId); }

    @Transactional
    public Map<String,Object> resubmitCorrections(TenantPrincipal actor,UUID applicationId) {
        Map<String,Object> app=ownedApplication(actor,applicationId);
        if(!"DEFECTIVE".equals(app.get("verification_status"))) throw new BusinessRuleViolationException("CORRECTION_NOT_REQUESTED","No correction request is outstanding.");
        jdbc.update("update app.admission_application set verification_status='PENDING',status='SUBMITTED',defect_notes=null,updated_by=? where tenant_id=? and id=?",
                actor.userAccountId(),actor.tenantId(),applicationId);
        return ownedApplication(actor,applicationId);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> scrutinyQueue(TenantPrincipal actor, UUID institutionId) {
        authorize(actor,"APPLICATION_VERIFY",institutionId);
        return jdbc.queryForList("select * from app.admission_application where tenant_id=? and institution_id=? and status<>'WITHDRAWN' order by submitted_at",
                actor.tenantId(),institutionId);
    }

    @Transactional
    public Map<String,Object> scrutinize(TenantPrincipal actor, UUID applicationId, String decision, String defectNotes) {
        Map<String,Object> app=application(actor,applicationId);
        authorize(actor,"APPLICATION_VERIFY",(UUID)app.get("institution_id"));
        if(!Set.of("VERIFIED","DEFECTIVE","REJECTED").contains(decision)) throw new BusinessRuleViolationException("VALIDATION_FAILED","Unsupported scrutiny decision.");
        if("VERIFIED".equals(decision)) {
            Integer notClean=jdbc.queryForObject("select count(*) from jsonb_array_elements(?::jsonb) d join app.file_object f on f.tenant_id=? and f.file_id=(d->>'fileId')::uuid where f.purpose='ADMISSION_DOCUMENT' and (f.status<>'ACTIVE' or f.scan_status<>'CLEAN')",
                    Integer.class,String.valueOf(app.get("documents_json")),actor.tenantId());
            if(notClean!=null&&notClean>0) throw new BusinessRuleViolationException("ADMISSION_DOCUMENT_NOT_CLEAN","Every attached document must pass virus scanning before verification.");
        }
        String nextStatus="REJECTED".equals(decision)?"WITHDRAWN":"DEFECTIVE".equals(decision)?"SUBMITTED":"SCRUTINIZED";
        jdbc.update("update app.admission_application set verification_status=?,status=?,defect_notes=?,correction_requested_at=case when ?='DEFECTIVE' then now() else correction_requested_at end,updated_by=? where tenant_id=? and id=?",
                decision,nextStatus,defectNotes,decision,actor.userAccountId(),actor.tenantId(),applicationId);
        return application(actor,applicationId);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> meritLists(TenantPrincipal actor, UUID cycleId) {
        Map<String,Object> cycle=one("select * from app.admission_cycle where tenant_id=? and id=?",actor.tenantId(),cycleId);
        authorize(actor,"MERIT_LIST_GENERATE",(UUID)cycle.get("institution_id"));
        return jdbc.queryForList("select * from app.merit_list where tenant_id=? and cycle_id=? order by round_number desc",actor.tenantId(),cycleId);
    }

    @Transactional
    public Map<String,Object> generateMeritList(TenantPrincipal actor, UUID cycleId, int round) {
        Map<String,Object> cycle=one("select * from app.admission_cycle where tenant_id=? and id=?",actor.tenantId(),cycleId);
        authorize(actor,"MERIT_LIST_GENERATE",(UUID)cycle.get("institution_id"));
        if(round<1) throw new BusinessRuleViolationException("VALIDATION_FAILED","Merit round must be positive.");
        Integer duplicate=jdbc.queryForObject("select count(*) from app.merit_list where tenant_id=? and cycle_id=? and round_number=?",
                Integer.class,actor.tenantId(),cycleId,round);
        if(duplicate!=null&&duplicate>0) throw new BusinessRuleViolationException("MERIT_ROUND_EXISTS","A merit list already exists for this round.");
        UUID list=UUID.randomUUID();
        jdbc.update("insert into app.merit_list(tenant_id,id,cycle_id,round_number,created_by) values(?,?,?,?,?)",
                actor.tenantId(),list,cycleId,round,actor.userAccountId());
        List<Map<String,Object>> apps=jdbc.queryForList("""
          select a.id,a.qualifying_marks_percent,a.category from app.admission_application a
          where a.tenant_id=? and a.cycle_id=? and a.status in('SCRUTINIZED','SHORTLISTED')
            and a.verification_status='VERIFIED' and a.fee_status in('CONFIRMED','WAIVED')
          order by a.qualifying_marks_percent desc,a.submitted_at asc,a.id
          """,actor.tenantId(),cycleId);
        int rank=0;
        for(Map<String,Object> a:apps){
            rank++;
            UUID item=UUID.randomUUID();
            String quota=String.valueOf(a.get("category"));
            jdbc.update("insert into app.merit_list_item(tenant_id,id,merit_list_id,application_id,merit_rank,computed_score,allocated_quota) values(?,?,?,?,?,?,?)",
                    actor.tenantId(),item,list,a.get("id"),rank,a.get("qualifying_marks_percent"),quota);
            jdbc.update("update app.admission_application set status='SHORTLISTED',updated_by=? where tenant_id=? and id=?",
                    actor.userAccountId(),actor.tenantId(),a.get("id"));
        }
        return one("select * from app.merit_list where tenant_id=? and id=?",actor.tenantId(),list);
    }

    @Transactional
    public Map<String,Object> approveMeritList(TenantPrincipal actor, UUID listId) {
        Map<String,Object> list=one("select ml.*,c.institution_id from app.merit_list ml join app.admission_cycle c on c.tenant_id=ml.tenant_id and c.id=ml.cycle_id where ml.tenant_id=? and ml.id=?",actor.tenantId(),listId);
        String role=TenantContext.currentActiveRoleCode();
        if(!Set.of("PRINCIPAL","COLLEGE_PRINCIPAL","SCHOOL_PRINCIPAL","VICE_CHANCELLOR","PRODUCT_OWNER").contains(role))
            throw new org.springframework.security.access.AccessDeniedException("MERIT_APPROVAL_NON_DELEGABLE");
        Integer delegated=jdbc.queryForObject("select count(*) from app.delegation where tenant_id=? and delegate_user_id=? and status='ACTIVE' and starts_at<=now() and ends_at>now() and (process_code is null or process_code in('ALL','MERIT_LIST_APPROVE'))",
                Integer.class,actor.tenantId(),actor.userAccountId());
        if(delegated!=null&&delegated>0) throw new org.springframework.security.access.AccessDeniedException("MERIT_APPROVAL_NON_DELEGABLE");
        authorize(actor,"MERIT_LIST_APPROVE",(UUID)list.get("institution_id"));
        jdbc.update("update app.merit_list set status='APPROVED',approved_by_user_id=?,approved_at=now() where tenant_id=? and id=? and status='PROVISIONAL'",
                actor.userAccountId(),actor.tenantId(),listId);
        return one("select * from app.merit_list where tenant_id=? and id=?",actor.tenantId(),listId);
    }

    @Transactional
    public Map<String,Object> allocateSeats(TenantPrincipal actor, UUID listId) {
        Map<String,Object> list=one("select ml.*,c.institution_id,c.total_seats,c.quota_capacity,c.id as cycle_id from app.merit_list ml join app.admission_cycle c on c.tenant_id=ml.tenant_id and c.id=ml.cycle_id where ml.tenant_id=? and ml.id=?",actor.tenantId(),listId);
        authorize(actor,"SEAT_ALLOCATE",(UUID)list.get("institution_id"));
        if(!Set.of("APPROVED","PUBLISHED").contains(list.get("status"))) throw new BusinessRuleViolationException("MERIT_LIST_NOT_APPROVED","Merit list must be approved before allocation.");
        List<Map<String,Object>> items=jdbc.queryForList("select i.* from app.merit_list_item i where i.tenant_id=? and i.merit_list_id=? and i.seat_status='OFFERED' and not exists (select 1 from app.seat_allotment s where s.tenant_id=i.tenant_id and s.merit_list_id=i.merit_list_id and s.application_id=i.application_id) order by i.merit_rank",actor.tenantId(),listId);
        int max=((Number)list.get("total_seats")).intValue(),count=0;
        Map<String,Object> capacities=readJsonObject(list.get("quota_capacity"));
        Map<String,Integer> used=new HashMap<>();
        for(Map<String,Object> item:items){
            if(count>=max) break;
            String quota=String.valueOf(item.get("allocated_quota"));
            int capacity=((Number)capacities.getOrDefault(quota,0)).intValue();
            if(used.getOrDefault(quota,0)>=capacity) continue;
            UUID allotment=UUID.randomUUID();
            jdbc.update("insert into app.seat_allotment(tenant_id,id,cycle_id,merit_list_id,merit_list_item_id,application_id,quota_code,created_by,updated_by) values(?,?,?,?,?,?,?,?,?)",
                    actor.tenantId(),allotment,list.get("cycle_id"),listId,item.get("id"),item.get("application_id"),quota,actor.userAccountId(),actor.userAccountId());
            used.merge(quota,1,Integer::sum); count++;
        }
        return Map.of("meritListId",listId,"offeredSeats",count);
    }

    @Transactional
    public Map<String,Object> decideSeat(TenantPrincipal actor, UUID allotmentId, String decision) {
        Map<String,Object> seat=one("select s.*,c.institution_id from app.seat_allotment s join app.admission_cycle c on c.tenant_id=s.tenant_id and c.id=s.cycle_id where s.tenant_id=? and s.id=?",actor.tenantId(),allotmentId);
        authorize(actor,"SEAT_ALLOCATE",(UUID)seat.get("institution_id"));
        if(!Set.of("ACCEPTED","DECLINED").contains(decision)) throw new BusinessRuleViolationException("VALIDATION_FAILED","Seat decision must be ACCEPTED or DECLINED.");
        if(!"OFFERED".equals(seat.get("status"))) throw new BusinessRuleViolationException("SEAT_ALREADY_DECIDED","Only an offered seat can be accepted or declined.");
        jdbc.update("update app.seat_allotment set status=?,accepted_at=case when ?='ACCEPTED' then now() else null end,updated_by=? where tenant_id=? and id=?",
                decision,decision,actor.userAccountId(),actor.tenantId(),allotmentId);
        jdbc.update("update app.merit_list_item set seat_status=? where tenant_id=? and id=?",
                decision,actor.tenantId(),seat.get("merit_list_item_id"));
        if("ACCEPTED".equals(decision)) jdbc.update("update app.admission_application set status='ADMITTED',updated_by=? where tenant_id=? and id=?",
                actor.userAccountId(),actor.tenantId(),seat.get("application_id"));
        return one("select * from app.seat_allotment where tenant_id=? and id=?",actor.tenantId(),allotmentId);
    }

    @Transactional
    public Map<String,Object> confirmApplicationFee(TenantPrincipal actor,UUID applicationId,String reference) {
        Map<String,Object> app=application(actor,applicationId);
        authorize(actor,"APPLICATION_FEE_VIEW",(UUID)app.get("institution_id"));
        requireCleanReceipt(actor,(UUID)app.get("application_fee_receipt_file_id"),(UUID)app.get("institution_id"));
        jdbc.update("update app.admission_application set fee_status='CONFIRMED',application_fee_reference=?,updated_by=? where tenant_id=? and id=?",
                reference,actor.userAccountId(),actor.tenantId(),applicationId);
        return application(actor,applicationId);
    }

    @Transactional
    public Map<String,Object> confirmAdmissionFee(TenantPrincipal actor, UUID applicationId,String reference) {
        Map<String,Object> app=application(actor,applicationId);
        authorize(actor,"APPLICATION_FEE_VIEW",(UUID)app.get("institution_id"));
        requireCleanReceipt(actor,(UUID)app.get("admission_fee_receipt_file_id"),(UUID)app.get("institution_id"));
        Integer accepted=jdbc.queryForObject("select count(*) from app.seat_allotment where tenant_id=? and application_id=? and status='ACCEPTED'",
                Integer.class,actor.tenantId(),applicationId);
        if(accepted==null||accepted!=1) throw new BusinessRuleViolationException("SEAT_NOT_ACCEPTED","Admission fee can only be confirmed after seat acceptance.");
        jdbc.update("update app.admission_application set admission_fee_status='CONFIRMED',admission_fee_reference=?,updated_by=? where tenant_id=? and id=?",
                reference,actor.userAccountId(),actor.tenantId(),applicationId);
        jdbc.update("update app.seat_allotment set admission_fee_confirmed_at=now(),updated_by=? where tenant_id=? and application_id=? and status='ACCEPTED'",
                actor.userAccountId(),actor.tenantId(),applicationId);
        return application(actor,applicationId);
    }

    @Transactional
    public StudentProfileResponse convertToStudent(TenantPrincipal actor, UUID applicationId) {
        Map<String,Object> app=application(actor,applicationId);
        authorize(actor,"SEAT_ALLOCATE",(UUID)app.get("institution_id"));
        Integer ready=jdbc.queryForObject("select count(*) from app.seat_allotment where tenant_id=? and application_id=? and status='ACCEPTED' and admission_fee_confirmed_at is not null",
                Integer.class,actor.tenantId(),applicationId);
        if(ready==null||ready!=1||!"ADMITTED".equals(app.get("status"))||!"CONFIRMED".equals(app.get("admission_fee_status")))
            throw new BusinessRuleViolationException("ADMISSION_CONVERSION_NOT_READY","Applicant must be admitted, have an accepted seat, and have confirmed admission fee.");
        if(app.get("student_profile_id")!=null) throw new BusinessRuleViolationException("ALREADY_CONVERTED","Application already has a student profile.");
        StudentProfileResponse profile=students.createProfileOnly(actor,new StudentProfileOnlyRequest((UUID)app.get("institution_id"),
                String.valueOf(app.get("applicant_name")),(LocalDate)app.get("date_of_birth"),LocalDate.now()));
        jdbc.update("update app.admission_application set student_profile_id=?,updated_by=? where tenant_id=? and id=?",
                profile.id(),actor.userAccountId(),actor.tenantId(),applicationId);
        return profile;
    }

    public UUID institutionForApplicant(TenantPrincipal actor,UUID applicationId) { return (UUID)ownedApplication(actor,applicationId).get("institution_id"); }
    private Map<String,Object> ownedApplication(TenantPrincipal actor,UUID id) {
        Map<String,Object> app=application(actor,id);
        if(!actor.userAccountId().equals(app.get("created_by"))) throw new ResourceNotFoundException("Admission record not found.");
        return app;
    }
    private Map<String,Object> application(TenantPrincipal actor, UUID id) {
        return one("select * from app.admission_application where tenant_id=? and id=?",actor.tenantId(),id);
    }
    private void requireCleanReceipt(TenantPrincipal actor,UUID fileId,UUID institutionId) {
        if(fileId==null) throw new BusinessRuleViolationException("FEE_RECEIPT_REQUIRED","Upload a fee receipt before confirming payment.");
        Integer clean=jdbc.queryForObject("select count(*) from app.file_object where tenant_id=? and file_id=? and institution_id=? and purpose='ADMISSION_DOCUMENT' and status='ACTIVE' and scan_status='CLEAN'",
                Integer.class,actor.tenantId(),fileId,institutionId);
        if(clean==null||clean!=1) throw new BusinessRuleViolationException("FEE_RECEIPT_NOT_CLEAN","Fee receipt must be scanned and clean.");
    }
    private Map<String,Object> readJsonObject(Object raw) {
        if(raw instanceof Map<?,?> map) { Map<String,Object> result=new HashMap<>(); map.forEach((k,v)->result.put(String.valueOf(k),v)); return result; }
        try { return json.readValue(String.valueOf(raw),new TypeReference<Map<String,Object>>(){}); }
        catch(Exception ex) { throw new BusinessRuleViolationException("VALIDATION_FAILED","Quota capacity must be a JSON object."); }
    }
    private Map<String,Object> one(String sql,Object... args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Admission record not found.");
        return rows.get(0);
    }
    private void authorize(TenantPrincipal actor,String action,UUID institution) {
        permissions.authorize(new RbacPrincipal(actor.tenantId(),actor.userAccountId()),action,
                institution==null?RbacScope.tenant():RbacScope.institution(institution),RbacTarget.NONE);
    }
    private void authorizeAny(TenantPrincipal actor,UUID institution,String... actions) {
        RbacPrincipal principal=new RbacPrincipal(actor.tenantId(),actor.userAccountId());
        RbacScope scope=institution==null?RbacScope.tenant():RbacScope.institution(institution);
        for(String action:actions) if(permissions.can(principal,action,scope,RbacTarget.NONE).allowed()) return;
        throw new org.springframework.security.access.AccessDeniedException("The active role cannot view admission cycles.");
    }
    private static String required(Map<String,Object> m,String k) {
        Object v=m.get(k); if(v==null||String.valueOf(v).isBlank()) throw new BusinessRuleViolationException("VALIDATION_FAILED",k+" is required.");
        return String.valueOf(v).trim();
    }
    private static UUID uuid(Map<String,Object> m,String k){return UUID.fromString(required(m,k));}
    private static int integer(Map<String,Object> m,String k){return Integer.parseInt(required(m,k));}
    private static BigDecimal decimal(Object v){try{return new BigDecimal(String.valueOf(v));}catch(Exception e){throw new BusinessRuleViolationException("VALIDATION_FAILED","A valid numeric value is required.");}}
    private static LocalDate date(Object v){return v==null||String.valueOf(v).isBlank()?null:LocalDate.parse(String.valueOf(v));}
    private static String serialize(Object v){try{return new ObjectMapper().writeValueAsString(v);}catch(Exception e){throw new IllegalArgumentException("Invalid JSON value.",e);}}
}
