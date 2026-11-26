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

 @Test void workingDayPolicyHonorsGraceAndConfiguredWeekdays() throws Exception {
  UUID tenant=UUID.randomUUID(), institution=UUID.randomUUID();
  try(var c=DriverManager.getConnection(System.getenv("EDU360_T10_JDBC_URL"),System.getenv("EDU360_T10_JDBC_USER"),System.getenv("EDU360_T10_JDBC_PASSWORD"))) {
   c.setAutoCommit(false);
   try {
    try(var st=c.createStatement()) {
     st.execute("SELECT set_config('app.tenant_id','" + tenant + "',true)");
     st.execute("SELECT set_config('app.institution_scope','LIST',true)");
     st.execute("SELECT set_config('app.institution_ids','" + institution + "',true)");
    }
    try(var ps=c.prepareStatement("INSERT INTO app.library_fine_policy(id,tenant_id,institution_id,grace_period_days,daily_amount,day_basis,working_days,fine_cap,is_placeholder_default) VALUES (?,?,?,?,?,'WORKING_DAYS','[1,3,5]'::jsonb,?,false)")) {
     ps.setObject(1,UUID.randomUUID());ps.setObject(2,tenant);ps.setObject(3,institution);ps.setInt(4,2);ps.setBigDecimal(5,new BigDecimal("3.50"));ps.setBigDecimal(6,new BigDecimal("100.00"));ps.executeUpdate();
    }
    try(var ps=c.prepareStatement("SELECT app.fn_library_fine_amount(?,?,current_date-10),(SELECT count(*)*3.50 FROM generate_series(current_date-10+2+1,current_date,interval '1 day') d WHERE EXTRACT(ISODOW FROM d)::integer IN (1,3,5)),app.fn_library_fine_amount(?,?,current_date-1)")) {
     ps.setObject(1,tenant);ps.setObject(2,institution);ps.setObject(3,tenant);ps.setObject(4,institution);
     try(var rs=ps.executeQuery()){assertThat(rs.next()).isTrue();assertThat(rs.getBigDecimal(1)).isEqualByComparingTo(rs.getBigDecimal(2));assertThat(rs.getBigDecimal(3)).isEqualByComparingTo("0.00");}
    }
   } finally {c.rollback();}
  }
 }
 @Test void t9ViewReturnsFiveColumnContractAndDynamicBalance() throws Exception {
  UUID tenant=UUID.randomUUID(), institution=UUID.randomUUID(), student=UUID.randomUUID(), book=UUID.randomUUID();
  try(var c=DriverManager.getConnection(System.getenv("EDU360_T10_JDBC_URL"),System.getenv("EDU360_T10_JDBC_USER"),System.getenv("EDU360_T10_JDBC_PASSWORD"))) {
   c.setAutoCommit(false);
   try {
    try(var st=c.createStatement()){st.execute("SELECT set_config('app.tenant_id','" + tenant + "',true)");st.execute("SELECT set_config('app.institution_scope','LIST',true)");st.execute("SELECT set_config('app.institution_ids','" + institution + "',true)");}
    try(var ps=c.prepareStatement("INSERT INTO app.library_fine_policy(id,tenant_id,institution_id,daily_amount,fine_cap) VALUES (?,?,?,?,?)")){ps.setObject(1,UUID.randomUUID());ps.setObject(2,tenant);ps.setObject(3,institution);ps.setBigDecimal(4,new BigDecimal("4.00"));ps.setBigDecimal(5,new BigDecimal("100.00"));ps.executeUpdate();}
    try(var ps=c.prepareStatement("INSERT INTO app.library_catalog(id,tenant_id,institution_id,title) VALUES (?,?,?,'T9 test book')")){ps.setObject(1,book);ps.setObject(2,tenant);ps.setObject(3,institution);ps.executeUpdate();}
    try(var ps=c.prepareStatement("INSERT INTO app.book_circulation(id,tenant_id,institution_id,book_id,borrower_user_id,borrower_role,due_date) VALUES (?,?,?,?,?,'STUDENT',current_date-2)")){ps.setObject(1,UUID.randomUUID());ps.setObject(2,tenant);ps.setObject(3,institution);ps.setObject(4,book);ps.setObject(5,student);ps.executeUpdate();}
    try(var ps=c.prepareStatement("SELECT tenant_id,institution_id,student_id,has_library_dues,outstanding_fine_balance FROM app.v_student_library_dues_flag WHERE student_id=?")) {
     ps.setObject(1,student);try(var rs=ps.executeQuery()){assertThat(rs.next()).isTrue();assertThat(rs.getObject(1,UUID.class)).isEqualTo(tenant);assertThat(rs.getObject(2,UUID.class)).isEqualTo(institution);assertThat(rs.getObject(3,UUID.class)).isEqualTo(student);assertThat(rs.getBoolean(4)).isTrue();assertThat(rs.getBigDecimal(5)).isEqualByComparingTo("8.00");}
    }
   } finally {c.rollback();}
  }
 }

}
