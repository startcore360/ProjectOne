package com.edu360.api.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.edu360.api.tenant.TenantRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named="EDU360_T10_JDBC_URL",matches=".+")
class DispatchWorkerIT {
    @Test
    void fallbackLeaseRecoveryAndTenantIsolationUsePostgresStateMachine() {
        UUID tenantA=UUID.randomUUID(), tenantB=UUID.randomUUID();
        UUID institutionA=UUID.randomUUID(), institutionB=UUID.randomUUID();
        try(HikariDataSource dsA=dataSource(tenantA); HikariDataSource dsB=dataSource(tenantB)) {
            JdbcTemplate a=new JdbcTemplate(dsA), b=new JdbcTemplate(dsB);
            UUID campaign=campaign(a,tenantA,institutionA);
            UUID inApp=dispatch(a,tenantA,institutionA,campaign,"IN_APP",1,"QUEUED",null);
            UUID push=dispatch(a,tenantA,institutionA,campaign,"PUSH",2,"WAITING",null);
            UUID sms=dispatch(a,tenantA,institutionA,campaign,"SMS",3,"WAITING",null);

            UUID otherCampaign=campaign(b,tenantB,institutionB);
            UUID otherTenantDispatch=dispatch(b,tenantB,institutionB,otherCampaign,"IN_APP",1,"QUEUED",null);

            UUID retryCampaign=campaign(a,tenantA,institutionA);
            UUID expiredLease=dispatch(a,tenantA,institutionA,retryCampaign,"SMS",1,"PROCESSING","now()-interval '6 minutes'");

            DispatchProvider fake=new DispatchProvider() {
                @Override public boolean supports(String channel) {
                    return List.of("IN_APP","PUSH","SMS").contains(channel);
                }
                @Override public DispatchProviderResult send(DispatchMessage message) {
                    if("IN_APP".equals(message.channel())) throw new IllegalStateException("in-app adapter unavailable");
                    return DispatchProviderResult.accepted("accepted-"+message.channel().toLowerCase());
                }
            };
            DispatchWorker worker=new DispatchWorker(mock(TenantRepository.class),a,List.of(fake));
            worker.drainTenant();

            Map<String,Object> inAppRow=a.queryForMap("SELECT status,failure_reason,attempt_count FROM app.message_dispatch WHERE id=?",inApp);
            assertThat(inAppRow.get("status")).isEqualTo("FAILED");
            assertThat(inAppRow.get("failure_reason").toString()).contains("adapter unavailable");
            assertThat(a.queryForObject("SELECT status FROM app.message_dispatch WHERE id=?",String.class,push)).isEqualTo("SENT");
            assertThat(a.queryForObject("SELECT status FROM app.message_dispatch WHERE id=?",String.class,sms)).isEqualTo("CANCELLED");
            assertThat(a.queryForObject("SELECT status FROM app.communication_campaign WHERE id=?",String.class,campaign)).isEqualTo("SENT");
            assertThat(a.queryForObject("SELECT status FROM app.message_dispatch WHERE id=?",String.class,expiredLease)).isEqualTo("SENT");
            assertThat(a.queryForObject("SELECT attempt_count FROM app.message_dispatch WHERE id=?",Integer.class,expiredLease)).isEqualTo(2);
            assertThat(b.queryForObject("SELECT status FROM app.message_dispatch WHERE id=?",String.class,otherTenantDispatch)).isEqualTo("QUEUED");
        }
    }

    private HikariDataSource dataSource(UUID tenant) {
        HikariConfig config=new HikariConfig();
        config.setJdbcUrl(System.getenv("EDU360_T10_JDBC_URL"));
        config.setUsername(System.getenv("EDU360_T10_JDBC_USER"));
        config.setPassword(System.getenv("EDU360_T10_JDBC_PASSWORD"));
        config.setPoolName("t10-dispatch-it-"+tenant.toString().substring(0,8));
        config.setMaximumPoolSize(4);
        config.setConnectionInitSql("SELECT set_config('app.tenant_id','"+tenant+"',false),set_config('app.institution_scope','ALL',false)");
        return new HikariDataSource(config);
    }

    private UUID campaign(JdbcTemplate jdbc,UUID tenant,UUID institution) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO app.communication_campaign(id,tenant_id,institution_id,name,status,created_by) VALUES (?,?,?,'dispatch integration','QUEUED',?)",
                id,tenant,institution,UUID.randomUUID());
        return id;
    }

    private UUID dispatch(JdbcTemplate jdbc,UUID tenant,UUID institution,UUID campaign,String channel,
                          int order,String status,String claimedAt) {
        UUID id=UUID.randomUUID();
        String sql="INSERT INTO app.message_dispatch(id,tenant_id,institution_id,campaign_id,channel,attempt_order,message_body,status,claimed_at,attempt_count) "+
                "VALUES (?,?,?,?,?,?,? ,?,"+(claimedAt==null?"NULL":claimedAt)+",CASE WHEN ?='PROCESSING' THEN 1 ELSE 0 END)";
        jdbc.update(sql,id,tenant,institution,campaign,channel,order,"integration message",status,status);
        return id;
    }
}
