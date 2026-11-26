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
    private void allowAny(UUID tenant,UUID user,UUID institution,String... actions) {
        ScopeType active=com.edu360.api.tenant.TenantContext.currentActiveScopeType();
        RbacScope scope=active==ScopeType.TENANT?RbacScope.tenant():RbacScope.institution(institution);
        RbacPrincipal principal=new RbacPrincipal(tenant,user);
        for(String action:actions) if(permissions.can(principal,action,scope,RbacTarget.NONE).allowed()) return;
        permissions.authorize(principal,actions[0],scope,RbacTarget.NONE);
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
        if(r.targetKind()!=null && !Set.of("ROLE","INSTITUTION").contains(r.targetKind()))
            throw new BusinessRuleViolationException("UNSUPPORTED_NOTICE_TARGET","Notice targets are role- or institution-scoped in this release.");
        if("INSTITUTION".equals(r.targetKind()) && !r.institutionId().equals(r.targetId()))
            throw new BusinessRuleViolationException("INVALID_NOTICE_TARGET","Institution target must match the notice institution.");
        String role=Objects.toString(com.edu360.api.tenant.TenantContext.currentActiveRoleCode(),"");
        String action=role.equals("UNIVERSITY_ADMIN")?"NOTICE_PUBLISH_UNIVERSITY":
            Set.of("PROFESSOR","ASSOCIATE_PROFESSOR","ASSISTANT_PROFESSOR","HEAD_OF_DEPARTMENT","CLASS_TEACHER","SUBJECT_TEACHER","DEPARTMENT_COORDINATOR").contains(role)
                ?"NOTICE_PUBLISH_CLASS_DEPT":"NOTICE_PUBLISH_INSTITUTION";
        allow(tenant,p.userAccountId(),action,r.institutionId());
        if(r.urgent()) allow(tenant,p.userAccountId(),"NOTICE_EMERGENCY",r.institutionId());
        UUID id=UUID.randomUUID();
        String targetRolesJson;
        try {
            targetRolesJson=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(r.targetRoles()==null?List.of():r.targetRoles());
        } catch(Exception e) {
            throw new IllegalArgumentException(e);
        }
        jdbc.update("""
          INSERT INTO app.notice_bulletin(id,tenant_id,institution_id,title,body,target_roles_json,is_urgent,requires_acknowledgment,
             published_at,status,created_by) VALUES (?,?,?,?,?,?::jsonb,?,?,now(),'PUBLISHED',?)
          """,id,tenant,r.institutionId(),r.title().trim(),r.body().trim(),
          targetRolesJson,
          r.urgent(),r.requiresAcknowledgment(),p.userAccountId());
        if(r.targetKind()!=null && !r.targetKind().equals("ROLE")) {
            jdbc.update("INSERT INTO app.notice_target(id,tenant_id,institution_id,notice_id,target_kind,target_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),tenant,r.institutionId(),id,r.targetKind(),r.targetId());
        }
        if(r.targetRoles()!=null) for(String targetRole:r.targetRoles()) {
            jdbc.update("INSERT INTO app.notice_target(id,tenant_id,institution_id,notice_id,target_kind,role_code) VALUES (?,?,?,?,'ROLE',?)",
                UUID.randomUUID(),tenant,r.institutionId(),id,targetRole);
        }
        audit.record(AuditEventType.CREATE,null,null,null,"Notice bulletin "+id+" published");
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
        Map<String,Object> recipient;
        try {
            recipient=jdbc.queryForMap("SELECT email,mobile FROM app.user_account WHERE tenant_id=? AND user_account_id=? AND status='ACTIVE'",
                tenant,r.recipientUserId());
        } catch(org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new BusinessRuleViolationException("RECIPIENT_NOT_ACTIVE","Recipient must be an active user in this tenant.");
        }
        String recipientEmail=(String)recipient.get("email");
        String recipientPhone=(String)recipient.get("mobile");
        List<String> chain=List.of("IN_APP","PUSH","SMS","EMAIL");
        List<String> requested=r.channels()==null||r.channels().isEmpty()?chain:r.channels();
        List<String> selected=chain.stream().filter(requested::contains).toList();
        if(selected.isEmpty()) throw new BusinessRuleViolationException("CHANNEL_REQUIRED","Choose at least one supported channel.");
        UUID campaign=UUID.randomUUID();
        jdbc.update("INSERT INTO app.communication_campaign(id,tenant_id,institution_id,name,target_roles_json,channels_json,status,created_by) VALUES (?,?,?,'Direct dispatch','[]'::jsonb,?::jsonb,'QUEUED',?)",
            campaign,tenant,r.institutionId(),json(selected),p.userAccountId());
        // First selected channel is claimable; later channels remain WAITING until a failure advances the chain.
        UUID first=null;
        for(int i=0;i<selected.size();i++) {
            String channel=selected.get(i);
            UUID id=UUID.randomUUID(); if(first==null) first=id;
            jdbc.update("""
                INSERT INTO app.message_dispatch(id,tenant_id,institution_id,campaign_id,channel,attempt_order,
                    recipient_user_id,phone,email,message_body,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,id,tenant,r.institutionId(),campaign,channel,i+1,r.recipientUserId(),recipientPhone,recipientEmail,r.body(),
                    i==0?"QUEUED":"WAITING");
        }
        audit.record(AuditEventType.CREATE,null,null,null,"Communication campaign "+campaign+" queued");
        return Map.of("campaignId",campaign,"firstDispatchId",first,"status","QUEUED","fallbackOrder",selected);
    }
    @Transactional
    public Map<String,Object> recordDispatchOutcome(UUID tenant,TenantPrincipal p,UUID institution,UUID dispatchId,String status,String failureReason,String carrierMessageId) {
        allow(tenant,p.userAccountId(),"MESSAGE_SEND",institution);
        if(!Set.of("SENT","DELIVERED","FAILED").contains(Objects.toString(status,"")))
            throw new BusinessRuleViolationException("INVALID_DISPATCH_STATUS","Outcome must be SENT, DELIVERED or FAILED.");
        Map<String,Object> row=jdbc.queryForMap("SELECT campaign_id,attempt_order FROM app.message_dispatch WHERE id=? AND tenant_id=? AND institution_id=? AND status='QUEUED' FOR UPDATE",dispatchId,tenant,institution);
        int changed=jdbc.update("""
            UPDATE app.message_dispatch SET status=?,failure_reason=?,carrier_message_id=?,
              sent_at=CASE WHEN ? IN ('SENT','DELIVERED') THEN now() ELSE sent_at END,
              delivered_at=CASE WHEN ?='DELIVERED' THEN now() ELSE delivered_at END
            WHERE id=? AND tenant_id=? AND institution_id=? AND status='QUEUED'
            """,status,failureReason,carrierMessageId,status,status,dispatchId,tenant,institution);
        if(changed!=1) throw new BusinessRuleViolationException("DISPATCH_NOT_QUEUED","Dispatch is no longer awaiting a delivery outcome.");
        UUID campaign=(UUID)row.get("campaign_id");
        UUID next=null;
        if("FAILED".equals(status)) {
            List<Map<String,Object>> pending=jdbc.queryForList("""
                SELECT id FROM app.message_dispatch WHERE tenant_id=? AND campaign_id=? AND status='WAITING'
                 ORDER BY attempt_order LIMIT 1 FOR UPDATE
                """,tenant,campaign);
            if(!pending.isEmpty()) {
                next=(UUID)pending.get(0).get("id");
                jdbc.update("UPDATE app.message_dispatch SET status='QUEUED' WHERE id=? AND tenant_id=?",next,tenant);
            } else jdbc.update("UPDATE app.communication_campaign SET status='FAILED' WHERE id=? AND tenant_id=?",campaign,tenant);
        } else {
            jdbc.update("UPDATE app.message_dispatch SET status='CANCELLED' WHERE tenant_id=? AND campaign_id=? AND status='WAITING'",tenant,campaign);
            jdbc.update("UPDATE app.communication_campaign SET status='SENT' WHERE id=? AND tenant_id=?",campaign,tenant);
        }
        return Map.of("campaignId",campaign,"status",status,"nextDispatchId",next==null?"":next.toString());
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
    @Transactional(readOnly=true)
    public List<Map<String,Object>> libraryCopies(UUID tenant,TenantPrincipal p,UUID institution,UUID bookId) {
        allow(tenant,p.userAccountId(),"BOOK_SEARCH_RESERVE",institution);
        return jdbc.queryForList("""
            SELECT id,accession_number,status FROM app.book_copy
             WHERE tenant_id=? AND institution_id=? AND book_id=? ORDER BY accession_number
            """,tenant,institution,bookId);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> libraryCirculations(UUID tenant,TenantPrincipal p,UUID institution) {
        allow(tenant,p.userAccountId(),"BOOK_ISSUE_RETURN",institution);
        return jdbc.queryForList("""
            SELECT c.id,c.book_id,b.title,c.copy_id,cp.accession_number,c.borrower_user_id,
                   c.borrower_role,c.issued_at,c.due_date,c.returned_at,c.status,
                   CASE WHEN c.returned_at IS NULL THEN app.fn_library_fine_amount(c.tenant_id,c.institution_id,c.due_date)
                        ELSE c.accrued_fine END AS current_fine
              FROM app.book_circulation c
              JOIN app.library_catalog b ON b.id=c.book_id AND b.tenant_id=c.tenant_id
              LEFT JOIN app.book_copy cp ON cp.id=c.copy_id AND cp.tenant_id=c.tenant_id
             WHERE c.tenant_id=? AND c.institution_id=?
             ORDER BY c.returned_at NULLS FIRST,c.due_date
             LIMIT 500
            """,tenant,institution);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> libraryFines(UUID tenant,TenantPrincipal p,UUID institution) {
        allow(tenant,p.userAccountId(),"FINE_COLLECT",institution);
        return jdbc.queryForList("""
            SELECT f.id,f.circulation_id,f.amount,f.amount_paid,(f.amount-f.amount_paid) AS outstanding,
                   f.status,f.assessed_at,c.borrower_user_id,b.title
              FROM app.library_fine f
              JOIN app.book_circulation c ON c.id=f.circulation_id AND c.tenant_id=f.tenant_id
              JOIN app.library_catalog b ON b.id=c.book_id AND b.tenant_id=c.tenant_id
             WHERE f.tenant_id=? AND f.institution_id=? AND f.status='OUTSTANDING'
             ORDER BY f.assessed_at DESC
             LIMIT 200
            """,tenant,institution);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> libraryReservations(UUID tenant,TenantPrincipal p,UUID institution) {
        allow(tenant,p.userAccountId(),"RESERVATION_MANAGE",institution);
        return jdbc.queryForList("""
            SELECT r.id,r.book_id,b.title,r.borrower_user_id,r.queue_position,r.status,r.created_at
              FROM app.library_reservation r JOIN app.library_catalog b
                ON b.id=r.book_id AND b.tenant_id=r.tenant_id
             WHERE r.tenant_id=? AND r.institution_id=? AND r.status='WAITING'
             ORDER BY b.title,r.queue_position
            """,tenant,institution);
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
    public Map<String,Object> addBookCopy(UUID tenant,TenantPrincipal p,UUID institution,UUID bookId,String accessionNumber) {
        allow(tenant,p.userAccountId(),"CATALOG_MANAGE",institution);
        if(accessionNumber==null||accessionNumber.isBlank()||accessionNumber.length()>80) throw new BusinessRuleViolationException("INVALID_ACCESSION","A unique accession number is required.");
        jdbc.queryForObject("SELECT id FROM app.library_catalog WHERE id=? AND tenant_id=? AND institution_id=? FOR UPDATE",UUID.class,bookId,tenant,institution);
        UUID copy=UUID.randomUUID();
        jdbc.update("INSERT INTO app.book_copy(id,tenant_id,institution_id,book_id,accession_number,status) VALUES (?,?,?,?,?,'AVAILABLE')",copy,tenant,institution,bookId,accessionNumber.trim());
        jdbc.update("UPDATE app.library_catalog SET total_copies=total_copies+1,available_copies=available_copies+1 WHERE id=? AND tenant_id=? AND institution_id=?",bookId,tenant,institution);
        return Map.of("copyId",copy,"status","AVAILABLE");
    }
    @Transactional
    public Map<String,Object> withdrawBookCopy(UUID tenant,TenantPrincipal p,UUID institution,UUID copyId) {
        allow(tenant,p.userAccountId(),"CATALOG_MANAGE",institution);
        Map<String,Object> copy=jdbc.queryForMap("SELECT book_id FROM app.book_copy WHERE id=? AND tenant_id=? AND institution_id=? AND status='AVAILABLE' FOR UPDATE",copyId,tenant,institution);
        int changed=jdbc.update("UPDATE app.book_copy SET status='WITHDRAWN' WHERE id=? AND tenant_id=? AND institution_id=? AND status='AVAILABLE'",copyId,tenant,institution);
        if(changed!=1) throw new BusinessRuleViolationException("COPY_NOT_AVAILABLE","Only an available copy can be withdrawn.");
        int catalogChanged=jdbc.update("UPDATE app.library_catalog SET total_copies=total_copies-1,available_copies=available_copies-1 WHERE id=? AND tenant_id=? AND institution_id=? AND total_copies>0 AND available_copies>0",copy.get("book_id"),tenant,institution);
        if(catalogChanged!=1) throw new BusinessRuleViolationException("CATALOG_COPY_COUNT_INVALID","Catalog copy counts are inconsistent; withdrawal was rolled back.");
        return Map.of("copyId",copyId,"status","WITHDRAWN");
    }
    @Transactional
    public Map<String,Object> issueBook(UUID tenant,TenantPrincipal p,LibraryController.IssueRequest r) {
        allow(tenant,p.userAccountId(),"BOOK_ISSUE_RETURN",r.institutionId());
        String borrowerType=jdbc.queryForObject("SELECT user_type FROM app.user_account WHERE tenant_id=? AND user_account_id=? AND status='ACTIVE' FOR UPDATE",String.class,tenant,r.borrowerUserId());
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
        if("STUDENT".equals(actualRole)) {
            Integer linked=jdbc.queryForObject("SELECT count(*) FROM app.student_profile WHERE tenant_id=? AND institution_id=? AND user_account_id=? AND status='ACTIVE'",Integer.class,tenant,r.institutionId(),r.borrowerUserId());
            if(linked==null||linked==0) throw new BusinessRuleViolationException("BORROWER_OUT_OF_SCOPE","Student is not active in this institution.");
        } else if("FACULTY".equals(actualRole)) {
            Integer linked=jdbc.queryForObject("""
                SELECT count(*) FROM app.staff_profile p JOIN app.staff_assignment a ON a.tenant_id=p.tenant_id AND a.staff_profile_id=p.staff_profile_id
                 WHERE p.tenant_id=? AND a.institution_id=? AND p.user_account_id=? AND p.employment_status='ACTIVE'
                   AND a.status='ACTIVE' AND a.effective_from<=current_date AND (a.effective_to IS NULL OR a.effective_to>=current_date)
                """,Integer.class,tenant,r.institutionId(),r.borrowerUserId());
            if(linked==null||linked==0) throw new BusinessRuleViolationException("BORROWER_OUT_OF_SCOPE","Faculty member is not active in this institution.");
        }
        Integer limit=switch(actualRole){case "STUDENT"->3;case "FACULTY"->10;default->throw new BusinessRuleViolationException("BORROW_ROLE_UNSUPPORTED","Borrow limits are defined for Student (3) and Faculty (10).");};
        Integer active=jdbc.queryForObject("SELECT count(*) FROM app.book_circulation WHERE tenant_id=? AND institution_id=? AND borrower_user_id=? AND returned_at IS NULL",Integer.class,tenant,r.institutionId(),r.borrowerUserId());
        if(active!=null&&active>=limit) throw new BusinessRuleViolationException("BORROW_LIMIT_REACHED","Borrow limit reached for role "+actualRole+": "+limit);
        if(r.dueDate()==null || r.dueDate().isBefore(java.time.LocalDate.now())) throw new BusinessRuleViolationException("INVALID_DUE_DATE","Due date must be today or later.");
        UUID id=UUID.randomUUID();
        int changed=jdbc.update("UPDATE app.book_copy SET status='ISSUED' WHERE id=? AND tenant_id=? AND institution_id=? AND book_id=? AND status='AVAILABLE'",r.copyId(),tenant,r.institutionId(),r.bookId());
        if(changed!=1) throw new BusinessRuleViolationException("COPY_UNAVAILABLE","Book copy is not available.");
        int availableChanged=jdbc.update("UPDATE app.library_catalog SET available_copies=available_copies-1 WHERE id=? AND tenant_id=? AND institution_id=? AND available_copies>0",r.bookId(),tenant,r.institutionId());
        if(availableChanged!=1) throw new BusinessRuleViolationException("COPY_UNAVAILABLE","Catalog availability is exhausted.");
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
        jdbc.update("UPDATE app.book_circulation SET returned_at=now(),status='RETURNED' WHERE id=? AND tenant_id=? AND institution_id=?",circulation,tenant,institution);
        if(row.get("copy_id")!=null) jdbc.update("UPDATE app.book_copy SET status='AVAILABLE' WHERE id=? AND tenant_id=? AND institution_id=?",row.get("copy_id"),tenant,institution);
        int catalogChanged=jdbc.update("UPDATE app.library_catalog SET available_copies=LEAST(total_copies,available_copies+1) WHERE id=? AND tenant_id=? AND institution_id=?",row.get("book_id"),tenant,institution);
        if(catalogChanged!=1) throw new BusinessRuleViolationException("CATALOG_ITEM_NOT_FOUND","The circulation catalog item is no longer available.");
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
        Integer eligible=jdbc.queryForObject("""
            SELECT count(*) FROM app.user_account u
             WHERE u.tenant_id=? AND u.user_account_id=? AND u.status='ACTIVE'
               AND (EXISTS(SELECT 1 FROM app.student_profile s WHERE s.tenant_id=u.tenant_id AND s.institution_id=? AND s.user_account_id=u.user_account_id AND s.status='ACTIVE')
                 OR EXISTS(SELECT 1 FROM app.staff_profile p JOIN app.staff_assignment a ON a.tenant_id=p.tenant_id AND a.staff_profile_id=p.staff_profile_id
                            WHERE p.tenant_id=u.tenant_id AND p.user_account_id=u.user_account_id AND p.employment_status='ACTIVE'
                              AND a.institution_id=? AND a.status='ACTIVE' AND a.effective_from<=current_date AND (a.effective_to IS NULL OR a.effective_to>=current_date)))
            """,Integer.class,tenant,r.borrowerUserId(),r.institutionId(),r.institutionId());
        if(eligible==null||eligible==0) throw new BusinessRuleViolationException("BORROWER_OUT_OF_SCOPE","Reservation borrower must be an active student or staff member of this institution.");
        jdbc.queryForObject("SELECT id FROM app.library_catalog WHERE id=? AND tenant_id=? AND institution_id=? FOR UPDATE",UUID.class,r.bookId(),tenant,r.institutionId());
        Long next=jdbc.queryForObject("SELECT COALESCE(max(queue_position),0)+1 FROM app.library_reservation WHERE tenant_id=? AND institution_id=? AND book_id=? AND status='WAITING'",Long.class,tenant,r.institutionId(),r.bookId());
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO app.library_reservation(id,tenant_id,institution_id,book_id,borrower_user_id,queue_position) VALUES (?,?,?,?,?,?)",id,tenant,r.institutionId(),r.bookId(),r.borrowerUserId(),next);
        return Map.of("reservationId",id,"queuePosition",next);
    }
    @Transactional
    public Map<String,Object> finePolicy(UUID tenant,TenantPrincipal p,UUID institution) {
        allow(tenant,p.userAccountId(),"LIBRARY_SETTINGS_SET",institution);
        jdbc.update("INSERT INTO app.library_fine_policy(id,tenant_id,institution_id) VALUES (?,?,?) ON CONFLICT(tenant_id,institution_id) DO NOTHING",
                UUID.randomUUID(),tenant,institution);
        return jdbc.queryForMap("SELECT grace_period_days,calculation_mode,daily_amount,day_basis,fine_cap,working_days::text AS working_days,is_placeholder_default FROM app.library_fine_policy WHERE tenant_id=? AND institution_id=?",tenant,institution);
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
        allowAny(tenant,p.userAccountId(),institution,"DRIVER_ROUTE_VIEW","TRANSPORT_MANAGE");
        return jdbc.queryForList("""
          SELECT r.id,r.route_name,r.vehicle_number,r.driver_name,r.driver_phone,r.total_capacity,r.active,
                 r.allocated_seats,r.transport_fee_code
            FROM app.transport_route r
           WHERE r.tenant_id=? AND r.institution_id=? ORDER BY r.route_name
          """,tenant,institution);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> transportStops(UUID tenant,TenantPrincipal p,UUID institution,UUID routeId) {
        allow(tenant,p.userAccountId(),"DRIVER_ROUTE_VIEW",institution);
        return jdbc.queryForList("""
            SELECT id,route_id,stop_name,pickup_time,drop_time,fee_amount,stop_order
              FROM app.route_stop WHERE tenant_id=? AND institution_id=? AND route_id=?
             ORDER BY stop_order
            """,tenant,institution,routeId);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> transportAllocations(UUID tenant,TenantPrincipal p,UUID institution) {
        allow(tenant,p.userAccountId(),"TRANSPORT_MANAGE",institution);
        return jdbc.queryForList("""
            SELECT a.id,a.route_id,r.route_name,a.stop_id,s.stop_name,a.passenger_user_id,
                   a.status,a.allocated_at,s.fee_amount,r.transport_fee_code
              FROM app.transport_allocation a
              JOIN app.transport_route r ON r.id=a.route_id AND r.tenant_id=a.tenant_id
              JOIN app.route_stop s ON s.id=a.stop_id AND s.tenant_id=a.tenant_id
             WHERE a.tenant_id=? AND a.institution_id=?
             ORDER BY a.allocated_at DESC LIMIT 500
            """,tenant,institution);
    }
    @Transactional
    public Map<String,Object> cancelTransportAllocation(UUID tenant,TenantPrincipal p,UUID institution,UUID allocationId) {
        allow(tenant,p.userAccountId(),"TRANSPORT_MANAGE",institution);
        int changed=jdbc.update("""
            UPDATE app.transport_allocation SET status='CANCELLED'
             WHERE id=? AND tenant_id=? AND institution_id=? AND status='ACTIVE'
            """,allocationId,tenant,institution);
        if(changed!=1) throw new BusinessRuleViolationException("ALLOCATION_NOT_ACTIVE","Active transport allocation not found.");
        return Map.of("allocationId",allocationId,"status","CANCELLED");
    }
    @Transactional
    public Map<String,Object> cancelTransportRoute(UUID tenant,TenantPrincipal p,UUID institution,UUID routeId) {
        allow(tenant,p.userAccountId(),"TRANSPORT_MANAGE",institution);
        int changed=jdbc.update("UPDATE app.transport_route SET active=false WHERE id=? AND tenant_id=? AND institution_id=? AND active=true",routeId,tenant,institution);
        if(changed!=1) throw new BusinessRuleViolationException("ROUTE_NOT_ACTIVE","Active transport route not found.");
        jdbc.update("UPDATE app.transport_allocation SET status='CANCELLED' WHERE route_id=? AND tenant_id=? AND institution_id=? AND status='ACTIVE'",routeId,tenant,institution);
        return Map.of("routeId",routeId,"status","CANCELLED");
    }
    @Transactional
    public Map<String,Object> createRoute(UUID tenant,TenantPrincipal p,TransportController.RouteRequest r) {
        allow(tenant,p.userAccountId(),"TRANSPORT_MANAGE",r.institutionId());
        if(r.capacity()<1||r.stops()==null||r.stops().isEmpty()||r.routeName()==null||r.routeName().isBlank()
            ||r.vehicleNumber()==null||r.vehicleNumber().isBlank()||r.driverName()==null||r.driverName().isBlank())
            throw new BusinessRuleViolationException("INVALID_ROUTE","Route, vehicle, driver, capacity and at least one stop are required.");
        if(r.stops().stream().anyMatch(s->s.name()==null||s.name().isBlank()||s.order()<0||s.feeAmount()==null||s.feeAmount().signum()<0)
            ||r.stops().stream().map(TransportController.StopRequest::order).distinct().count()!=r.stops().size())
            throw new BusinessRuleViolationException("INVALID_ROUTE_STOPS","Stops require unique non-negative order, name and non-negative fee.");
        if(r.transportFeeCode()!=null&&!r.transportFeeCode().isBlank()) {
            Integer feeHead=jdbc.queryForObject("SELECT count(*) FROM app.fee_head WHERE tenant_id=? AND fee_head_code=? AND category='TRANSPORT' AND status='ACTIVE'",
                Integer.class,tenant,r.transportFeeCode());
            if(feeHead==null||feeHead==0) throw new BusinessRuleViolationException("TRANSPORT_FEE_HEAD_NOT_FOUND","Choose an active TRANSPORT fee head code.");
        }
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
        if(r.passengerUserId()==null||r.routeId()==null||r.stopId()==null)
            throw new BusinessRuleViolationException("INVALID_ALLOCATION","Route, stop and passenger are required.");
        Integer activeUser=jdbc.queryForObject("""
            SELECT count(*) FROM app.user_account u
             WHERE u.tenant_id=? AND u.user_account_id=? AND u.status='ACTIVE'
               AND (EXISTS(SELECT 1 FROM app.student_profile s WHERE s.tenant_id=u.tenant_id AND s.institution_id=? AND s.user_account_id=u.user_account_id AND s.status='ACTIVE')
                 OR EXISTS(SELECT 1 FROM app.staff_profile p JOIN app.staff_assignment a ON a.tenant_id=p.tenant_id AND a.staff_profile_id=p.staff_profile_id
                            WHERE p.tenant_id=u.tenant_id AND p.user_account_id=u.user_account_id AND p.employment_status='ACTIVE'
                              AND a.institution_id=? AND a.status='ACTIVE' AND a.effective_from<=current_date AND (a.effective_to IS NULL OR a.effective_to>=current_date)))
            """,Integer.class,tenant,r.passengerUserId(),r.institutionId(),r.institutionId());
        if(activeUser==null||activeUser==0) throw new BusinessRuleViolationException("PASSENGER_OUT_OF_SCOPE","Passenger must be an active student or staff member of this institution.");
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
