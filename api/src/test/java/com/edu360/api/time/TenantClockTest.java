package com.edu360.api.time;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Clock;import java.time.Instant;import java.time.ZoneOffset;import java.util.UUID;
import org.junit.jupiter.api.Test;import org.springframework.jdbc.core.JdbcTemplate;
class TenantClockTest {
 @Test void usesTenantTimeZoneForBusinessDate(){JdbcTemplate jdbc=mock(JdbcTemplate.class);UUID tenant=UUID.randomUUID();when(jdbc.queryForObject(any(String.class),eq(String.class),eq(tenant))).thenReturn("Asia/Kolkata");TenantClock clock=new TenantClock(jdbc,Clock.fixed(Instant.parse("2026-11-10T20:00:00Z"),ZoneOffset.UTC));assertThat(clock.today(tenant).toString()).isEqualTo("2026-11-11");}
}
