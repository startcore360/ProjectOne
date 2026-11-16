package com.edu360.api.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
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

    @Bean(name = {"transactionManager", "platformTransactionManager"})
    @org.springframework.context.annotation.Primary
    public PlatformTransactionManager testTransactionManager(
            @Qualifier("entityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        // Declare both names on this bean. Otherwise this PlatformTransactionManager makes
        // Boot's conditional default transaction manager back off, leaving the alias target absent.
        // Both names intentionally resolve to the same JPA manager and test EntityManagerFactory.
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}
