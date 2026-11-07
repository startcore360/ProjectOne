package com.edu360.api.config;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import com.edu360.api.tenant.TenantRepository;
import com.zaxxer.hikari.HikariDataSource;

/**
 * WHAT: a second, completely separate JPA stack - its own DataSource, EntityManagerFactory and
 * TransactionManager - for the handful of repositories that read or write a platform.* table
 * (platform.tenant, platform.plan, platform.platform_user, and the platformops ones). Everything
 * else in the app keeps using the default stack (JpaRepositoryConfig, backed by the @Primary
 * edu_app-connected TenantRoutingDataSource).
 *
 * WHY this has to be a second physical connection pool, not just "run this one query as a
 * different role": sql/03_bindings.sql grants edu_app no USAGE on the platform schema at all
 * (line 1014 - only "USAGE ON SCHEMA app, ref"), and every platform.* table's own GRANT
 * (lines 20-77 of that file) names edu_platform exclusively. Before this class existed,
 * TenantRepository/PlanRepository/PlatformUserRepository/etc. ran through the edu_app-backed
 * EntityManagerFactory like every other repository, and every single call failed in production
 * with Postgres's own "permission denied for table ..." - confirmed live against the real dev
 * Postgres (POST /api/platform/tenants -> 500, stack trace: org.postgresql.util.PSQLException:
 * ERROR: permission denied for table plan). That is also why DevAdminSeeder already opens its
 * own raw JDBC connection as edu_platform instead of going through a repository - this class
 * generalizes that same necessity into a real, reusable Spring-managed connection instead of a
 * one-off DriverManager call good for exactly one INSERT statement.
 *
 * WHY @Transactional on a caller like TenantManagementService.createTenant() does not get
 * atomicity across BOTH this EntityManagerFactory and the default one in the same method: by
 * design, Spring Data routes each repository to the TransactionManager named in its own
 * @EnableJpaRepositories (see JpaRepositoryConfig vs. this class), so a service method that calls
 * both an edu_app-backed repository and a platform-backed one is really running two independent
 * transactions, not one. Accepted here as the same kind of best-effort trade-off the codebase
 * already makes for Keycloak provisioning in the same method (also outside the DB transaction) -
 * true cross-role atomicity on the same physical Postgres database would require either sharing
 * one role (defeating the schema's own access-separation design) or two-phase commit, neither of
 * which this fix is the place to introduce.
 *
 * WHY "!test": see JpaRepositoryConfig's class comment - the test profile's single H2 database
 * has no edu_platform role to connect as, so this entire second stack is skipped for tests and
 * Spring Boot's default single-EMF auto-configuration handles everything instead.
 */
@Configuration
@Profile("!test")
@EnableJpaRepositories(
        basePackages = {"com.edu360.api.platform", "com.edu360.api.platformops"},
        basePackageClasses = TenantRepository.class,
        entityManagerFactoryRef = "platformEntityManagerFactory",
        transactionManagerRef = "platformTransactionManager"
)
public class PlatformJpaConfig {

    /**
     * Same JDBC URL/driver as the app's main pool (DataSourceProperties, bound from the usual
     * spring.datasource.* properties - this is one Postgres instance, not two) but its own
     * Hikari pool authenticating as edu_platform, per app.platform-admin-datasource.* (the same
     * credentials DevAdminSeeder already uses for its one-off dev insert).
     */
    @Bean(name = "platformDataSource")
    public HikariDataSource platformDataSource(
            DataSourceProperties properties,
            @Value("${app.platform-admin-datasource.username:edu_platform}") String username,
            @Value("${app.platform-admin-datasource.password:}") String password) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("platformPool");
        // Platform-schema traffic (tenant/plan provisioning, platform user management, a few
        // dashboards) is low-volume compared to the main tenant-data pool - a small pool is
        // enough and avoids holding open connections the platform schema rarely needs.
        dataSource.setMaximumPoolSize(5);
        return dataSource;
    }

    @Bean(name = "platformEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean platformEntityManagerFactory(
            @Qualifier("platformDataSource") DataSource platformDataSource,
            JpaProperties jpaProperties, HibernateProperties hibernateProperties) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(platformDataSource);
        // com.edu360.api.tenant is included only for Tenant.java itself (platform.tenant) - the
        // rest of that package (TenantContext, TenantRoutingDataSource, etc.) has no other
        // @Entity class, so nothing app-schema leaks into this persistence unit by scanning it.
        factory.setPackagesToScan(
                "com.edu360.api.platform",
                "com.edu360.api.platformops",
                "com.edu360.api.tenant");
        factory.setPersistenceUnitName("platform");

        // Same spring.jpa.* properties (ddl-auto, show-sql, database-platform) the default
        // persistence unit reads (see JpaRepositoryConfig.entityManagerFactory) - one Postgres
        // instance, so both units should honor the same dialect/ddl rules, just via different
        // Hikari pools authenticating as different roles.
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(jpaProperties.isShowSql());
        vendorAdapter.setDatabase(jpaProperties.getDatabase());
        vendorAdapter.setDatabasePlatform(jpaProperties.getDatabasePlatform());
        vendorAdapter.setGenerateDdl(jpaProperties.isGenerateDdl());
        factory.setJpaVendorAdapter(vendorAdapter);

        HibernateSettings settings = new HibernateSettings().ddlAuto(hibernateProperties::getDdlAuto);
        Map<String, Object> properties = hibernateProperties.determineHibernateProperties(
                jpaProperties.getProperties(), settings);
        factory.setJpaPropertyMap(properties);
        return factory;
    }

    @Bean(name = "platformTransactionManager")
    public PlatformTransactionManager platformTransactionManager(
            @Qualifier("platformEntityManagerFactory") LocalContainerEntityManagerFactoryBean platformEntityManagerFactory) {
        return new JpaTransactionManager(platformEntityManagerFactory.getObject());
    }
}
