package com.edu360.api.t10;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutiveAnalyticsService {
    private final ObjectProvider<JdbcTemplate> reportJdbc;
    private final PermissionEvaluatorService permissions;
    private final JdbcTemplate tenantJdbc;
    private final TransactionTemplate reportTransaction;
    public ExecutiveAnalyticsService(@Qualifier("eduReportJdbcTemplate") ObjectProvider<JdbcTemplate> reportJdbc,
            PermissionEvaluatorService permissions, @Qualifier("jdbcTemplate") JdbcTemplate tenantJdbc,
            @Qualifier("eduReportTransactionManager") ObjectProvider<org.springframework.transaction.PlatformTransactionManager> reportManager) {
        this.reportJdbc=reportJdbc; this.permissions=permissions; this.tenantJdbc=tenantJdbc;
        org.springframework.transaction.PlatformTransactionManager manager=reportManager.getIfAvailable();
        this.reportTransaction=manager==null?null:new TransactionTemplate(manager);
        if(this.reportTransaction!=null) this.reportTransaction.setReadOnly(true);
    }
    public Map<String,Object> dashboard(UUID tenant,TenantPrincipal p,UUID institution,LocalDate from,LocalDate to) {
        ScopeType active=TenantContext.currentActiveScopeType();
        permissions.authorize(new RbacPrincipal(tenant,p.userAccountId()),"REPORTS_VIEW",
                active==ScopeType.TENANT?RbacScope.tenant():RbacScope.institution(institution),RbacTarget.NONE);
        JdbcTemplate db=reportJdbc.getIfAvailable();
        if(db==null || reportTransaction==null) throw new BusinessRuleViolationException("REPORTING_CONNECTION_UNAVAILABLE","The dedicated read-only edu_report connection is not configured.");
        LocalDate end=to==null?LocalDate.now():to, start=from==null?end.minusDays(29):from;
        if(start.isAfter(end)||start.isBefore(end.minusYears(2))) throw new BusinessRuleViolationException("INVALID_REPORT_RANGE","Report range must be ordered and no longer than two years.");
        UUID reportDefinition=tenantJdbc.queryForObject("""
            INSERT INTO app.report_definition(id,tenant_id,institution_id,report_code,report_name)
            VALUES (?,?,?,'EXECUTIVE_DASHBOARD','Executive Dashboard')
            ON CONFLICT(tenant_id,institution_id,report_code) DO UPDATE SET report_name=EXCLUDED.report_name
            RETURNING id
            """,UUID.class,UUID.randomUUID(),tenant,institution);
        UUID execution=UUID.randomUUID();
        tenantJdbc.update("INSERT INTO app.report_execution(id,tenant_id,institution_id,report_definition_id,requested_by,from_date,to_date,status) VALUES (?,?,?,?,?,?,?,'RUNNING')",
            execution,tenant,institution,reportDefinition,p.userAccountId(),start,end);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("reportExecutionId",execution);
        out.put("period",Map.of("from",start,"to",end));
        try {
            Map<String,Object> metrics=reportTransaction.execute(tx -> {
                // Bind RLS scope locally to this read-only transaction; pooled reporting connections
                // never retain one tenant's GUCs for the next request.
                db.queryForObject("SELECT set_config('app.tenant_id', ?, true)",String.class,tenant.toString());
                db.queryForObject("SELECT set_config('app.institution_scope', 'LIST', true)",String.class);
                db.queryForObject("SELECT set_config('app.institution_ids', ?, true)",String.class,institution.toString());
                Map<String,Object> values=new LinkedHashMap<>();
                values.put("activeStudents",scalar(db,"SELECT count(*) FROM app.student_profile WHERE tenant_id=? AND institution_id=? AND status='ACTIVE'",tenant,institution));
                values.put("activeStaff",scalar(db,"SELECT count(DISTINCT p.staff_profile_id) FROM app.staff_profile p
                  JOIN app.staff_assignment a ON a.tenant_id=p.tenant_id AND a.staff_profile_id=p.staff_profile_id
                 WHERE p.tenant_id=? AND a.institution_id=? AND p.employment_status='ACTIVE'
                   AND a.status='ACTIVE' AND a.effective_from<=current_date AND (a.effective_to IS NULL OR a.effective_to>=current_date)",tenant,institution));
                values.put("libraryOutstandingFines",scalar(db,"SELECT COALESCE(sum(outstanding_fine_balance),0) FROM app.v_student_library_dues_flag WHERE tenant_id=? AND institution_id=?",tenant,institution));
                values.put("openLibraryCirculations",scalar(db,"SELECT count(*) FROM app.book_circulation WHERE tenant_id=? AND institution_id=? AND returned_at IS NULL",tenant,institution));
                values.put("transportAllocations",scalar(db,"SELECT count(*) FROM app.transport_allocation WHERE tenant_id=? AND institution_id=? AND status='ACTIVE'",tenant,institution));
                values.put("smsSent",scalar(db,"SELECT count(*) FROM app.message_dispatch WHERE tenant_id=? AND institution_id=? AND channel='SMS' AND status IN ('SENT','DELIVERED') AND created_at>=? AND created_at<?",tenant,institution,start.atStartOfDay().atOffset(ZoneOffset.UTC),end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
                return values;
            });
            if(metrics!=null) out.putAll(metrics);
            tenantJdbc.update("UPDATE app.report_execution SET status='SUCCEEDED',completed_at=now() WHERE id=? AND tenant_id=?",execution,tenant);
            return out;
        } catch(RuntimeException ex) {
            tenantJdbc.update("UPDATE app.report_execution SET status='FAILED',completed_at=now(),error_code='REPORT_QUERY_FAILED' WHERE id=? AND tenant_id=?",execution,tenant);
            throw ex;
        }
    }
    private Object scalar(JdbcTemplate db,String sql,Object... args) { return db.queryForObject(sql,Object.class,args); }
}
