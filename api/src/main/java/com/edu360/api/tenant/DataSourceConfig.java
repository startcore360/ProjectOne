package com.edu360.api.tenant;

import java.time.Duration;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariDataSource;

/**
 * WHAT: replaces Spring Boot's own auto-configured DataSource with two beans - a plain Hikari
 * pool built from the usual spring.datasource.* properties (the SHARED pool every SHARED
 * tenant uses, kept apart only by RLS), and TenantRoutingDataSource wrapping it as the
 * @Primary DataSource that Hibernate/JPA and every repository in the app actually use.
 *
 * WHY Spring Boot's own DataSourceAutoConfiguration does not also run and create a THIRD,
 * conflicting DataSource bean: that auto-configuration is conditional on no DataSource bean
 * already existing (@ConditionalOnMissingBean(DataSource.class)) - since this class defines
 * one already (the @Primary bean below), Spring Boot's own auto-configuration backs off
 * automatically. No manual exclusion needed.
 */
@Configuration
public class DataSourceConfig {

    // Takes Spring Boot's OWN auto-configured DataSourceProperties bean (bound from
    // spring.datasource.* automatically - no need to declare a second, duplicate one) and uses
    // its initializeDataSourceBuilder() method, which is what actually knows how to translate
    // "url" onto Hikari's differently-named setJdbcUrl(...) setter. Found by actually running
    // the test suite: binding @ConfigurationProperties(prefix = "spring.datasource") straight
    // onto a HikariDataSource does NOT work, because plain relaxed property binding has no way
    // to know "url" should map to a differently-named setter - only DataSourceProperties does.
    @Bean(name = "sharedDataSource")
    public HikariDataSource sharedDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource sharedDataSource, TenantSecretsResolver secretsResolver,
                                  @Value("${app.isolated-pools.max-cached:50}") int maxCachedPools,
                                  @Value("${app.isolated-pools.idle-timeout-minutes:30}") long idleTimeoutMinutes) {
        return new TenantRoutingDataSource(sharedDataSource, secretsResolver,
                maxCachedPools, Duration.ofMinutes(idleTimeoutMinutes));
    }
}
