package com.edu360.api.t10;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="EDU360_T10_JDBC_URL",matches=".+")
class LibraryOverdueFineIT {
 @Test void configuredFinePolicyCalculatesCalendarDaysAndHonorsCap() throws Exception {
  UUID tenant=UUID.randomUUID(), institution=UUID.randomUUID();
  try(var c=DriverManager.getConnection(System.getenv("EDU360_T10_JDBC_URL"),System.getenv("EDU360_T10_JDBC_USER"),System.getenv("EDU360_T10_JDBC_PASSWORD"))) {
   c.setAutoCommit(false);
   try {
    try(var st=c.createStatement()) {
     st.execute("SELECT set_config('app.tenant_id','" + tenant + "',true)");
     st.execute("SELECT set_config('app.institution_scope','LIST',true)");
     st.execute("SELECT set_config('app.institution_ids','" + institution + "',true)");
    }
    try(var ps=c.prepareStatement("""
      INSERT INTO app.library_fine_policy(id,tenant_id,institution_id,grace_period_days,daily_amount,day_basis,working_days,fine_cap,is_placeholder_default)
      VALUES (?,?,?,?,?,'CALENDAR_DAYS','[1,2,3,4,5,6,7]'::jsonb,?,false)
      """)) {
     ps.setObject(1,UUID.randomUUID());ps.setObject(2,tenant);ps.setObject(3,institution);ps.setInt(4,0);
     ps.setBigDecimal(5,new BigDecimal("7.50"));ps.setBigDecimal(6,new BigDecimal("20.00"));ps.executeUpdate();
    }
    try(var ps=c.prepareStatement("SELECT app.fn_library_fine_amount(?,?,current_date-5)")) {
     ps.setObject(1,tenant);ps.setObject(2,institution);
     try(var rs=ps.executeQuery()){assertThat(rs.next()).isTrue();assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("20.00");}
    }
   } finally {c.rollback();}
  }
 }
}
