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
   try(var rs=s.executeQuery("SELECT current_user, current_setting('default_transaction_read_only')")) {
    assertThat(rs.next()).isTrue();
    assertThat(rs.getString(1)).isEqualTo("edu_report");
    assertThat(rs.getString(2)).isEqualTo("on");
   }
  }
 }
}
