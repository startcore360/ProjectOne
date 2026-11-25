package com.edu360.api.tenant;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Dedicated reporting pool. Credentials must authenticate as the PostgreSQL edu_report role. */
@Configuration
@ConditionalOnProperty(prefix="app.edu-report.datasource",name="url")
public class EduReportDataSourceConfig {
 @Bean(name="eduReportDataSource")
 public DataSource dataSource(
        @Value("${app.edu-report.datasource.url}") String url,
        @Value("${app.edu-report.datasource.username}") String username,
        @Value("${app.edu-report.datasource.password}") String password,
        @Value("${app.edu-report.datasource.driver-class-name:org.postgresql.Driver}") String driver) {
  if (!"edu_report".equals(username)) {
   throw new IllegalStateException("The edu_report datasource must authenticate as PostgreSQL role edu_report.");
  }
  HikariDataSource ds=new HikariDataSource();
  ds.setJdbcUrl(url); ds.setUsername(username); ds.setPassword(password); ds.setDriverClassName(driver);
  ds.setPoolName("edu-report-readonly"); ds.setReadOnly(true);
  ds.setConnectionInitSql("SET default_transaction_read_only = on");
  return new LazyConnectionDataSourceProxy(ds);
 }
}
