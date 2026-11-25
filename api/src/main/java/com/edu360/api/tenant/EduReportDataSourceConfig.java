package com.edu360.api.tenant;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
/** Dedicated reporting pool. Credentials must authenticate as the PostgreSQL edu_report role. */
@Configuration
@ConditionalOnProperty(prefix="app.edu-report.datasource",name="url")
public class EduReportDataSourceConfig {
 @Bean(name="eduReportDataSourceProperties")
 @ConfigurationProperties("app.edu-report.datasource")
 public DataSourceProperties properties(){return new DataSourceProperties();}
 @Bean(name="eduReportDataSource")
 public HikariDataSource dataSource(@Qualifier("eduReportDataSourceProperties") DataSourceProperties p){
  HikariDataSource ds=p.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  ds.setPoolName("edu-report-readonly"); ds.setReadOnly(true);
  ds.setConnectionInitSql("SET default_transaction_read_only = on"); return ds;
 }
 @Bean(name="eduReportJdbcTemplate")
 public JdbcTemplate jdbc(@Qualifier("eduReportDataSource") DataSource ds){return new JdbcTemplate(ds);}
}
