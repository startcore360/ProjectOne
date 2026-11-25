package com.edu360.api.t10;

import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tenant-aware outbox worker. It visits only ACTIVE/TRIAL tenant databases, establishes the
 * same RLS context used by the nightly usage job, claims one dispatch at a time with
 * SKIP LOCKED, performs I/O outside the claim transaction, and advances the persisted fallback.
 */
@Component
public class DispatchWorker {
    private static final Logger log=LoggerFactory.getLogger(DispatchWorker.class);
    private static final int BATCH_SIZE=25;
    private final TenantRepository tenants;
    private final JdbcTemplate jdbc;
    private final List<DispatchProvider> providers;
    private final TransactionTemplate transaction;
    public DispatchWorker(TenantRepository tenants,@Qualifier("jdbcTemplate") JdbcTemplate jdbc,
            List<DispatchProvider> providers) {
        this.tenants=tenants; this.jdbc=jdbc; this.providers=providers;
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    @Scheduled(fixedDelayString="${app.dispatch.worker.fixed-delay-ms:5000}")
    public void poll() {
        for(Tenant tenant:tenants.findAll()) {
            if(tenant.getState()!=TenantState.ACTIVE && tenant.getState()!=TenantState.TRIAL) continue;
            try {
                TenantContext.set(tenant.getTenantId(),tenant.getIsolationMode(),tenant.getDbConnectionRef(),
                        "ALL",Set.of(),null,null);
                drainTenant();
            } catch(Exception ex) {
                log.error("Dispatch worker failed for tenant {}",tenant.getTenantId(),ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
    void drainTenant() {
        for(int i=0;i<BATCH_SIZE;i++) {
            DispatchMessage message=claim();
            if(message==null) return;
            try {
                DispatchProvider provider=providers.stream().filter(p->p.supports(message.channel())).findFirst()
                    .orElseThrow(()->new IllegalStateException("No adapter registered for "+message.channel()));
                DispatchProviderResult result=provider.send(message);
                markSent(message,result.providerMessageId());
            } catch(Exception ex) {
                markFailed(message,safeReason(ex));
            }
        }
    }
    private DispatchMessage claim() {
        return transaction.execute(status -> {
            // Recover claims abandoned by a process crash. Provider requests have a 10s timeout;
            // five minutes is deliberately conservative and downstream adapters are idempotent.
            jdbc.update("""
                UPDATE app.message_dispatch SET status='QUEUED',claimed_at=NULL
                 WHERE status='PROCESSING' AND claimed_at < now()-interval '5 minutes'
                """);
            List<DispatchMessage> rows=jdbc.query("""
                WITH candidate AS (
                  SELECT id FROM app.message_dispatch
                   WHERE status='QUEUED' ORDER BY created_at,attempt_order
                   LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE app.message_dispatch d
                   SET status='PROCESSING',claimed_at=now(),attempt_count=attempt_count+1
                  FROM candidate c
                 WHERE d.id=c.id
                RETURNING d.id,d.tenant_id,d.institution_id,d.campaign_id,d.recipient_user_id,
                          d.phone,d.email,d.message_body,
                          (SELECT created_by FROM app.communication_campaign WHERE id=d.campaign_id) AS created_by,
                          d.channel
                """,(rs,n)->new DispatchMessage(rs.getObject("id",UUID.class),
                    rs.getObject("tenant_id",UUID.class),rs.getObject("institution_id",UUID.class),
                    rs.getObject("campaign_id",UUID.class),rs.getObject("recipient_user_id",UUID.class),
                    rs.getObject("created_by",UUID.class),rs.getString("channel"),rs.getString("phone"),
                    rs.getString("email"),rs.getString("message_body")));
            return rows.isEmpty()?null:rows.get(0);
        });
    }
    private void markSent(DispatchMessage message,String providerId) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("""
                UPDATE app.message_dispatch SET status='SENT',sent_at=now(),claimed_at=NULL,carrier_message_id=?
                 WHERE id=? AND tenant_id=? AND status='PROCESSING'
                """,providerId,message.dispatchId(),message.tenantId());
            jdbc.update("UPDATE app.message_dispatch SET status='CANCELLED' WHERE tenant_id=? AND campaign_id=? AND status='WAITING'",
                message.tenantId(),message.campaignId());
            jdbc.update("UPDATE app.communication_campaign SET status='SENT' WHERE id=? AND tenant_id=?",
                message.campaignId(),message.tenantId());
        });
    }
    private void markFailed(DispatchMessage message,String reason) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("""
                UPDATE app.message_dispatch SET status='FAILED',failure_reason=?,claimed_at=NULL
                 WHERE id=? AND tenant_id=? AND status='PROCESSING'
                """,reason,message.dispatchId(),message.tenantId());
            List<Map<String,Object>> next=jdbc.queryForList("""
                SELECT id FROM app.message_dispatch WHERE tenant_id=? AND campaign_id=? AND status='WAITING'
                 ORDER BY attempt_order LIMIT 1 FOR UPDATE
                """,message.tenantId(),message.campaignId());
            if(next.isEmpty()) {
                jdbc.update("UPDATE app.communication_campaign SET status='FAILED' WHERE id=? AND tenant_id=?",
                    message.campaignId(),message.tenantId());
            } else {
                jdbc.update("UPDATE app.message_dispatch SET status='QUEUED' WHERE id=? AND tenant_id=?",
                    next.get(0).get("id"),message.tenantId());
                jdbc.update("UPDATE app.communication_campaign SET status='QUEUED' WHERE id=? AND tenant_id=?",
                    message.campaignId(),message.tenantId());
            }
        });
    }
    private String safeReason(Exception ex) {
        String value=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
        return value.length()>500?value.substring(0,500):value;
    }
}
