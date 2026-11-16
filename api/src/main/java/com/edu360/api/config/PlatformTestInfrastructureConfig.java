package com.edu360.api.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Test-only aliases for the platform JDBC service. Tests deliberately use one H2 database,
 * so the platform idempotency service shares the test datasource and transaction manager.
 * The separate edu_platform pool and grants remain mandatory in dev/prod.
 */
@Configuration
@Profile("test")
public class PlatformTestInfrastructureConfig {

    @Bean(name = "platformDataSource")
    public DataSource platformDataSource(@Qualifier("sharedDataSource") DataSource sharedDataSource) {
        return sharedDataSource;
    }

    @Bean(name = "platformTransactionManager")
    public PlatformTransactionManager platformTransactionManager(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        return transactionManager;
    }
}
