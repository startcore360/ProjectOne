package com.edu360.api.time;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.edu360.api.tenant.TenantContext;
/** Business clock sourced from app.tenant_local.time_zone, defaulting to Asia/Kolkata. */
@Component
public class TenantClock {
 public static final ZoneId DEFAULT_ZONE=ZoneId.of("Asia/Kolkata");
 private final JdbcTemplate jdbc;private final Clock clock;
 @Autowired public TenantClock(DataSource ds){this(new JdbcTemplate(ds),Clock.systemUTC());}
 public TenantClock(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
 public ZonedDateTime now(){return now(TenantContext.currentTenantId());}
 public LocalDate today(){return today(TenantContext.currentTenantId());}
 public ZonedDateTime now(UUID tenant){return Instant.now(clock).atZone(zoneFor(tenant));}
 public LocalDate today(UUID tenant){return now(tenant).toLocalDate();}
 public ZoneId zoneFor(UUID tenant){
  if(tenant==null)return DEFAULT_ZONE;
  try{return ZoneId.of(jdbc.queryForObject("SELECT time_zone FROM app.tenant_local WHERE tenant_id = ?",String.class,tenant));}
  catch(EmptyResultDataAccessException ex){return DEFAULT_ZONE;}
 }
}
