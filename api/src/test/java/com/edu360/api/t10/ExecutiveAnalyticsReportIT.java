package com.edu360.api.t10;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="EDU360_REPORT_JDBC_URL",matches=".+")
class ExecutiveAnalyticsReportIT {
 @Test void reportingCredentialIsReadOnlyEduReportRole() throws Exception {
  try(var c=DriverManager.getConnection(System.getenv("EDU360_REPORT_JDBC_URL"),System.getenv("EDU360_REPORT_JDBC_USER"),System.getenv("EDU360_REPORT_JDBC_PASSWORD"));
      var s=c.createStatement()) {
   try(var rs=s.executeQuery("""
      SELECT current_user,
             has_table_privilege(current_user,'app.student_profile','SELECT'),
             has_table_privilege(current_user,'app.student_profile','INSERT'),
             has_table_privilege(current_user,'app.student_profile','UPDATE'),
             has_table_privilege(current_user,'app.student_profile','DELETE')
      """)) {
    assertThat(rs.next()).isTrue();
    assertThat(rs.getString(1)).isEqualTo("edu_report");
    assertThat(rs.getBoolean(2)).isTrue();
    assertThat(rs.getBoolean(3)).isFalse();
    assertThat(rs.getBoolean(4)).isFalse();
    assertThat(rs.getBoolean(5)).isFalse();
   }
  }
 }
}
