package com.edu360.api.t10;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class T10DomainService {
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final AuditLogService audit;
    public T10DomainService(@Qualifier("jdbcTemplate") JdbcTemplate jdbc,PermissionEvaluatorService permissions,AuditLogService audit) {
        this.jdbc=jdbc; this.permissions=permissions; this.audit=audit;
    }
    private void allow(UUID tenant,UUID user,String action,UUID institution) {
        ScopeType active= com.edu360.api.tenant.TenantContext.currentActiveScopeType();
        RbacScope scope=active==ScopeType.TENANT?RbacScope.tenant():RbacScope.institution(institution);
        permissions.authorize(new RbacPrincipal(tenant,user),action,scope,RbacTarget.NONE);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> listNotices(UUID tenant,TenantPrincipal p,UUID institution) {
        allow(tenant,p.userAccountId(),"SELF_SERVICE_VIEW",institution);
        return jdbc.queryForList("""
            SELECT n.id AS notice_id,n.title,n.body,n.is_urgent,n.requires_acknowledgment,n.published_at,
                   EXISTS(SELECT 1 FROM app.notice_acknowledgment a WHERE a.tenant_id=n.tenant_id AND a.notice_id=n.id AND a.user_id=?) AS acknowledged
              FROM app.notice_bulletin n WHERE n.tenant_id=? AND n.institution_id=? AND n.status='PUBLISHED'
               AND (n.expires_at IS NULL OR n.expires_at>now())
               AND (n.target_roles_json='[]'::jsonb OR n.target_roles_json ? ?)
             ORDER BY n.is_urgent DESC,n.published_at DESC
            """,p.userAccountId(),tenant,institution,Objects.toString(com.edu360.api.tenant.TenantContext.currentActiveRoleCode(),""));
    }
    @Transactional
    public Map<String,Object> publishNotice(UUID tenant,TenantPrincipal p,NoticeBulletinController.NoticeRequest r) {
        if(r.institutionId()==null || r.title()==null || r.title().isBlank() || r.title().length()>160 || r.body()==null || r.body().isBlank())
            throw new BusinessRuleViolationException("VALIDATION_FAILED","Institution, title and body are required.");
        String role=Objects.toString(com.edu360.api.tenant.TenantContext.currentActiveRoleCode(),"");
        String action=role.equals("UNIVERSITY_ADMIN")?"NOTICE_PUBLISH_UNIVERSITY":
            Set.of("PROFESSOR","ASSOCIATE_PROFESSOR","ASSISTANT_PROFESSOR","HEAD_OF_DEPARTMENT","CLASS_TEACHER","SUBJECT_TEACHER","DEPARTMENT_COORDINATOR").contains(role)
                ?"NOTICE_PUBLISH_CLASS_DEPT":"NOTICE_PUBLISH_INSTITUTION";
        if("DEPARTMENT".equals(r.targetKind()) && r.targetId()!=null) {
            permissions.authorize(new RbacPrincipal(tenant,p.userAccountId()),action,
                RbacScope.department(r.institutionId(),r.targetId()),RbacTarget.NONE);
        } else allow(tenant,p.userAccountId(),action,r.institutionId());
        UUID id=UUID.randomUUID();
        jdbc.update("""
          INSERT INTO app.notice_bulletin(id,tenant_id,institution_id,title,body,target_roles_json,is_urgent,requires_acknowledgment,
             published_at,status,created_by) VALUES (?,?,?,?,?,?::jsonb,?,?,now(),'PUBLISHED',?)
          """,id,tenant,r.institutionId(),r.title().trim(),r.body().trim(),
          new org.postgresql.util.PGobject(){ {setType("jsonb"); try{setValue(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(r.targetRoles()==null?List.of():r.targetRoles()));}catch(Exception e){throw new IllegalArgumentException(e);} } },
          r.urgent(),r.requiresAcknowledgment(),p.userAccountId());
        if(r.targetKind()!=null && !r.targetKind().equals("ROLE")) {
            jdbc.update("INSERT INTO app.notice_target(id,tenant_id,institution_id,notice_id,target_kind,target_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),tenant,r.institutionId(),id,r.targetKind(),r.targetId());
        }
        if(r.targetRoles()!=null) for(String role:r.targetRoles()) {
            jdbc.update("INSERT INTO app.notice_target(id,tenant_id,institution_id,notice_id,target_kind,role_code) VALUES (?,?,?,?,'ROLE',?)",
                UUID.randomUUID(),tenant,r.institutionId(),id,role);
        }
        audit.record(AuditEventType.PLAN_CREATED,null,p.userAccountId().toString(),tenant.toString(),"Notice bulletin "+id+" published");
        return Map.of("noticeId",id,"status","PUBLISHED");
    }
    @Transactional
    public void acknowledgeNotice(UUID tenant,TenantPrincipal p,UUID institution,UUID notice) {
        allow(tenant,p.userAccountId(),"SELF_SERVICE_VIEW",institution);
        jdbc.update("""
          INSERT INTO app.notice_acknowledgment(id,tenant_id,institution_id,notice_id,user_id)
          SELECT ?,?,?,n.id,? FROM app.notice_bulletin n WHERE n.id=? AND n.tenant_id=? AND n.institution_id=?
             AND n.status='PUBLISHED' AND n.requires_acknowledgment=true
             AND (n.target_roles_json='[]'::jsonb OR n.target_roles_json ? ?)
          ON CONFLICT(tenant_id,notice_id,user_id) DO NOTHING
          """,UUID.randomUUID(),tenant,institution,p.userAccountId(),notice,tenant,institution,Objects.toString(com.edu360.api.tenant.TenantContext.currentActiveRoleCode(),""));
    }
    @Transactional
    public Map<String,Object> dispatch(UUID tenant,TenantPrincipal p,CommunicationDispatchController.DispatchRequest r) {
        allow(tenant,p.userAccountId(),"MESSAGE_SEND",r.institutionId());
        if(r.body()==null || r.body().isBlank() || r.recipientUserId()==null) throw new BusinessRuleViolationException("VALIDATION_FAILED","Recipient and message body are required.");
        UUID campaign=UUID.randomUUID();
        jdbc.update("INSERT INTO app.communication_campaign(id,tenant_id,institution_id,name,target_roles_json,channels_json,status,created_by) VALUES (?,?,?,'Direct dispatch','[]'::jsonb,?::jsonb,'QUEUED',?)",
            campaign,tenant,r.institutionId(),json(r.channels()==null?List.of("IN_APP","PUSH","SMS","EMAIL"):r.channels()),p.userAccountId());
        // Persist the fallback chain in priority order; provider workers can claim queued rows independently.
        List<String> chain=List.of("IN_APP","PUSH","SMS","EMAIL");
        List<String> requested=r.channels()==null||r.channels().isEmpty()?chain:r.channels();
        UUID first=null;
        for(String channel:chain) if(requested.contains(channel)) {
            UUID id=UUID.randomUUID(); if(first==null) first=id;
            jdbc.update("INSERT INTO app.message_dispatch(id,tenant_id,institution_id,campaign_id,channel,recipient_user_id,phone,email,message_body,status) VALUES (?,?,?,?,?,?,?,?,?,'QUEUED')",
                id,tenant,r.institutionId(),campaign,channel,r.recipientUserId(),r.phone(),r.email(),r.body());
        }
        audit.record(AuditEventType.PLAN_CREATED,null,p.userAccountId().toString(),tenant.toString(),"Communication campaign "+campaign+" queued");
        return Map.of("campaignId",campaign,"status","QUEUED","fallbackOrder",chain);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> dispatchHistory(UUID tenant,TenantPrincipal p,UUID institution) {
        allow(tenant,p.userAccountId(),"DELIVERY_FAILURES_VIEW",institution);
        return jdbc.queryForList("SELECT id,channel,status,failure_reason,sent_at,delivered_at,created_at FROM app.message_dispatch WHERE tenant_id=? AND institution_id=? ORDER BY created_at DESC LIMIT 200",tenant,institution);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> libraryCatalog(UUID tenant,TenantPrincipal p,UUID institution,String q) {
        allow(tenant,p.userAccountId(),"BOOK_SEARCH_RESERVE",institution);
        String term=q==null?"":q.trim();
        return jdbc.queryForList("SELECT id,isbn,title,author,category,total_copies,available_copies FROM app.library_catalog WHERE tenant_id=? AND institution_id=? AND (?='' OR title ILIKE '%'||?||'%' OR isbn ILIKE '%'||?||'%') ORDER BY title LIMIT 200",tenant,institution,term,term,term);
    }
    @Transactional
    public Map<String,Object> addBook(UUID tenant,TenantPrincipal p,LibraryController.BookRequest r) {
        allow(tenant,p.userAccountId(),"CATALOG_MANAGE",r.institutionId());
        if(r.title()==null||r.title().isBlank()||r.copies()<0) throw new BusinessRuleViolationException("VALIDATION_FAILED","A title and non-negative copy count are required.");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO app.library_catalog(id,tenant_id,institution_id,isbn,title,author,category,total_copies,available_copies) VALUES (?,?,?,?,?,?,?,?,?)",
          id,tenant,r.institutionId(),r.isbn(),r.title().trim(),r.author(),r.category(),r.copies(),r.copies());
        for(int i=1;i<=r.copies();i++) jdbc.update("INSERT INTO app.book_copy(id,tenant_id,institution_id,book_id,accession_number) VALUES (?,?,?,?,?)",
          UUID.randomUUID(),tenant,r.institutionId(),id,id.toString().substring(0,8)+"-"+i);
        return Map.of("bookId",id,"copies",r.copies());
    }
    @Transactional
    public Map<String,Object> issueBook(UUID tenant,TenantPrincipal p,LibraryController.IssueRequest r) {
        allow(tenant,p.userAccountId(),"BOOK_ISSUE_RETURN",r.institutionId());
        String borrowerType=jdbc.queryForObject("SELECT user_type FROM app.user_account WHERE tenant_id=? AND user_account_id=?",String.class,tenant,r.borrowerUserId());
        String actualRole;
        if("STUDENT".equalsIgnoreCase(borrowerType)) actualRole="STUDENT";
        else {
            Integer faculty=jdbc.queryForObject("""
                SELECT count(*) FROM app.user_role_assignment a JOIN app.role_definition d
                  ON d.tenant_id=a.tenant_id AND d.role_definition_id=a.role_definition_id
                 WHERE a.tenant_id=? AND a.user_account_id=? AND a.status='ACTIVE'
                   AND d.status='ACTIVE' AND d.role_code IN ('PROFESSOR','ASSOCIATE_PROFESSOR','ASSISTANT_PROFESSOR')
                """,Integer.class,tenant,r.borrowerUserId());
            actualRole=faculty!=null&&faculty>0?"FACULTY":"OTHER";
        }
        Integer limit=switch(actualRole){case "STUDENT"->3;case "FACULTY"->10;default->throw new BusinessRuleViolationException("BORROW_ROLE_UNSUPPORTED","Borrow limits are defined for Student (3) and Faculty (10).");};
        Integer active=jdbc.queryForObject("SELECT count(*) FROM app.book_circulation WHERE tenant_id=? AND institution_id=? AND borrower_user_id=? AND returned_at IS NULL",Integer.class,tenant,r.institutionId(),r.borrowerUserId());
        if(active!=null&&active>=limit) throw new BusinessRuleViolationException("BORROW_LIMIT_REACHED","Borrow limit reached for role "+r.borrowerRole()+": "+limit);
        UUID id=UUID.randomUUID();
        int changed=jdbc.update("UPDATE app.book_copy SET status='ISSUED' WHERE id=? AND tenant_id=? AND institution_id=? AND status='AVAILABLE'",r.copyId(),tenant,r.institutionId());
        if(changed!=1) throw new BusinessRuleViolationException("COPY_UNAVAILABLE","Book copy is not available.");
        jdbc.update("UPDATE app.library_catalog SET available_copies=available_copies-1 WHERE id=? AND tenant_id=? AND institution_id=? AND available_copies>0",r.bookId(),tenant,r.institutionId());
        jdbc.update("INSERT INTO app.book_circulation(id,tenant_id,institution_id,book_id,copy_id,borrower_user_id,borrower_role,due_date,status) VALUES (?,?,?,?,?,?,?,?, 'ISSUED')",
          id,tenant,r.institutionId(),r.bookId(),r.copyId(),r.borrowerUserId(),actualRole,r.dueDate());
        return Map.of("circulationId",id,"status","ISSUED","borrowLimit",limit);
    }
    @Transactional
    public Map<String,Object> returnBook(UUID tenant,TenantPrincipal p,UUID institution,UUID circulation) {
        allow(tenant,p.userAccountId(),"BOOK_ISSUE_RETURN",institution);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT book_id,copy_id,due_date,borrower_user_id FROM app.book_circulation WHERE id=? AND tenant_id=? AND institution_id=? AND returned_at IS NULL FOR UPDATE",circulation,tenant,institution);
        if(rows.isEmpty()) throw new BusinessRuleViolationException("CIRCULATION_NOT_OPEN","Open circulation not found.");
        Map<String,Object> row=rows.get(0);
        jdbc.update("UPDATE app.book_circulation SET returned_at=now(),status='RETURNED' WHERE id=? AND tenant_id=?",circulation,tenant);
        jdbc.update("UPDATE app.book_copy SET status='AVAILABLE' WHERE id=? AND tenant_id=?",row.get("copy_id"),tenant);
        jdbc.update("UPDATE app.library_catalog SET available_copies=available_copies+1 WHERE id=? AND tenant_id=?",row.get("book_id"),tenant);
        BigDecimal fine=jdbc.queryForObject("SELECT app.fn_library_fine_amount(?,?,?)",BigDecimal.class,tenant,institution,row.get("due_date"));
        if(fine!=null&&fine.signum()>0) {
            UUID fineId=UUID.randomUUID();
            jdbc.update("INSERT INTO app.library_fine(id,tenant_id,institution_id,circulation_id,amount,status) VALUES (?,?,?, ?,?,'OUTSTANDING')",fineId,tenant,institution,circulation,fine);
            jdbc.update("UPDATE app.book_circulation SET accrued_fine=? WHERE id=?",fine,circulation);
        }
        return Map.of("circulationId",circulation,"status","RETURNED","fineAssessed",fine==null?BigDecimal.ZERO:fine);
    }
    @Transactional
    public Map<String,Object> reserveBook(UUID tenant,TenantPrincipal p,LibraryController.ReservationRequest r) {
        allow(tenant,p.userAccountId(),"RESERVATION_MANAGE",r.institutionId());
        Long next=jdbc.queryForObject("SELECT COALESCE(max(queue_position),0)+1 FROM app.library_reservation WHERE tenant_id=? AND institution_id=? AND book_id=? AND status='WAITING'",Long.class,tenant,r.institutionId(),r.bookId());
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO app.library_reservation(id,tenant_id,institution_id,book_id,borrower_user_id,queue_position) VALUES (?,?,?,?,?,?)",id,tenant,r.institutionId(),r.bookId(),r.borrowerUserId(),next);
        return Map.of("reservationId",id,"queuePosition",next);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> finePolicy(UUID tenant,TenantPrincipal p,UUID institution) {
        allow(tenant,p.userAccountId(),"LIBRARY_SETTINGS_SET",institution);
        return jdbc.queryForMap("SELECT grace_period_days,calculation_mode,daily_amount,day_basis,fine_cap,working_days,is_placeholder_default FROM app.library_fine_policy WHERE tenant_id=? AND institution_id=?",tenant,institution);
    }
    @Transactional
    public Map<String,Object> updateFinePolicy(UUID tenant,TenantPrincipal p,LibraryController.FinePolicyRequest r) {
        allow(tenant,p.userAccountId(),"LIBRARY_SETTINGS_SET",r.institutionId());
        if(r.gracePeriodDays()<0||r.dailyAmount()==null||r.dailyAmount().signum()<0||r.fineCap()==null||r.fineCap().signum()<0||
           !List.of("FLAT_DAILY_AMOUNT").contains(r.calculationMode())||!List.of("CALENDAR_DAYS","WORKING_DAYS").contains(r.dayBasis())||
           (r.workingDays()==null||r.workingDays().isEmpty()||r.workingDays().stream().anyMatch(d->d<1||d>7)))
            throw new BusinessRuleViolationException("INVALID_FINE_POLICY","Fine policy values are invalid.");
        jdbc.update("UPDATE app.library_fine_policy SET grace_period_days=?,calculation_mode=?,daily_amount=?,day_basis=?,fine_cap=?,working_days=?::jsonb,is_placeholder_default=false,updated_at=now(),updated_by=? WHERE tenant_id=? AND institution_id=?",
          r.gracePeriodDays(),r.calculationMode(),r.dailyAmount(),r.dayBasis(),r.fineCap(),json(r.workingDays()),p.userAccountId(),tenant,r.institutionId());
        return finePolicy(tenant,p,r.institutionId());
    }
    @Transactional
    public Map<String,Object> payFine(UUID tenant,TenantPrincipal p,UUID institution,UUID fine) {
        allow(tenant,p.userAccountId(),"FINE_COLLECT",institution);
        int changed=jdbc.update("UPDATE app.library_fine SET amount_paid=amount,status='PAID' WHERE id=? AND tenant_id=? AND institution_id=? AND status='OUTSTANDING'",fine,tenant,institution);
        if(changed!=1) throw new BusinessRuleViolationException("FINE_NOT_OUTSTANDING","Fine not found or already paid.");
        return Map.of("fineId",fine,"status","PAID");
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> transportRoutes(UUID tenant,TenantPrincipal p,UUID institution) {
        allow(tenant,p.userAccountId(),"DRIVER_ROUTE_VIEW",institution);
        return jdbc.queryForList("""
          SELECT r.id,r.route_name,r.vehicle_number,r.driver_name,r.driver_phone,r.total_capacity,
             count(a.id) FILTER(WHERE a.status='ACTIVE') AS allocated_seats,r.transport_fee_code
          FROM app.transport_route r LEFT JOIN app.transport_allocation a ON a.route_id=r.id AND a.tenant_id=r.tenant_id
          WHERE r.tenant_id=? AND r.institution_id=? GROUP BY r.id ORDER BY r.route_name
          """,tenant,institution);
    }
    @Transactional
    public Map<String,Object> createRoute(UUID tenant,TenantPrincipal p,TransportController.RouteRequest r) {
        allow(tenant,p.userAccountId(),"TRANSPORT_MANAGE",r.institutionId());
        if(r.capacity()<1||r.stops()==null||r.stops().isEmpty()) throw new BusinessRuleViolationException("INVALID_ROUTE","Capacity and at least one stop are required.");
        UUID vehicleId=UUID.randomUUID();
        jdbc.update("INSERT INTO app.vehicle(id,tenant_id,institution_id,registration_number,vehicle_type,capacity) VALUES (?,?,?,?,'BUS',?)",
          vehicleId,tenant,r.institutionId(),r.vehicleNumber(),r.capacity());
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO app.transport_route(id,tenant_id,institution_id,route_name,vehicle_id,vehicle_number,driver_name,driver_phone,total_capacity,transport_fee_code) VALUES (?,?,?,?,?,?,?,?,?,?)",
          id,tenant,r.institutionId(),r.routeName(),vehicleId,r.vehicleNumber(),r.driverName(),r.driverPhone(),r.capacity(),r.transportFeeCode());
        for(var s:r.stops()) jdbc.update("INSERT INTO app.route_stop(id,tenant_id,institution_id,route_id,stop_name,pickup_time,drop_time,fee_amount,stop_order) VALUES (?,?,?,?,?,?,?,?,?)",
          UUID.randomUUID(),tenant,r.institutionId(),id,s.name(),s.pickupTime(),s.dropTime(),s.feeAmount(),s.order());
        return Map.of("routeId",id,"capacity",r.capacity(),"stops",r.stops().size());
    }
    @Transactional
    public Map<String,Object> allocateTransport(UUID tenant,TenantPrincipal p,TransportController.AllocationRequest r) {
        allow(tenant,p.userAccountId(),"TRANSPORT_MANAGE",r.institutionId());
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO app.transport_allocation(id,tenant_id,institution_id,route_id,stop_id,passenger_user_id) VALUES (?,?,?,?,?,?)",
          id,tenant,r.institutionId(),r.routeId(),r.stopId(),r.passengerUserId());
        return Map.of("allocationId",id,"status","ACTIVE");
    }
    private String json(Object value) {
        try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);}
        catch(Exception e){throw new IllegalArgumentException("Unable to encode JSON",e);}
    }
}
