package com.edu360.api.config;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import com.edu360.api.tenant.TenantRepository;

/**
 * WHAT: the app's default JPA repository scan - every repository EXCEPT the ones that read or
 * write a platform.* table (see PlatformJpaConfig for those) - plus the @Primary
 * "entityManagerFactory"/"transactionManager" beans those repositories (and every
 * @Transactional service that isn't platform-schema-only, e.g. AuditLogService) run on, bound to
 * the app's @Primary DataSource (TenantRoutingDataSource, connecting as edu_app).
 *
 * WHY this class has to exist at all, instead of just adding PlatformJpaConfig's
 * @EnableJpaRepositories on its own: the moment ANY @EnableJpaRepositories appears anywhere in
 * the app, Spring Boot's own JpaRepositoriesAutoConfiguration backs off entirely (it is
 * conditional on no such annotation existing) - so the normal, non-platform repositories
 * (UserAccountRepository, FileObjectRepository, every app.* one) would stop being created at all
 * unless something explicitly re-declares their scan. This class is that re-declaration.
 *
 * WHY entityManagerFactory/transactionManager ALSO have to be declared explicitly here, instead
 * of continuing to rely on Spring Boot's own JpaBaseConfiguration auto-configuring them (as it
 * did before PlatformJpaConfig existed): found by actually starting the app, not guessed - that
 * auto-configuration is conditional on no bean of type EntityManagerFactory already existing
 * ANYWHERE in the context, and conditions are matched by type, not name. The instant
 * PlatformJpaConfig defines its own "platformEntityManagerFactory" (also of type
 * EntityManagerFactory), Boot's auto-configuration sees that condition as already satisfied and
 * stops creating its own default one - even though that default one is a different bean with a
 * different name. Every real Spring Boot app with two JPA DataSources hits this same thing; the
 * fix is always to define both EntityManagerFactory beans explicitly, never just the extra one.
 * The JpaProperties/HibernateProperties wiring below exists only to keep reading the exact same
 * spring.jpa.* properties (ddl-auto, show-sql, database-platform) Boot's own auto-configuration
 * would have read, so this explicit bean behaves identically to the one it replaces.
 *
 * WHY "!test": the test profile runs everything against a single in-memory H2 database (one
 * user, "sa", no schema/role separation at all - see application-test.properties), so splitting
 * repositories across two EntityManagerFactories has nothing to buy there and would just make a
 * second Hikari pool try to authenticate against that same H2 instance as a user ("edu_platform")
 * that was never the one who created it, breaking every test. Excluding this profile lets Spring
 * Boot's own JpaRepositoriesAutoConfiguration/JpaBaseConfiguration handle the single-EMF case for
 * tests exactly as it did before this class existed.
 */
@Configuration
@Profile("!test")
@EnableJpaRepositories(
        basePackages = "com.edu360.api",
        // Whole packages, not a list of classes: PlatformJpaConfig scans these same packages, so a
        // repository added there and missed here would be registered twice and stop the app starting.
        excludeFilters = {
                @Filter(type = FilterType.REGEX, pattern = "com\\.edu360\\.api\\.(platform|platformops)\\..*"),
                @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TenantRepository.class)
        }
)
public class JpaRepositoryConfig {

    @Bean(name = "entityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource, JpaProperties jpaProperties, HibernateProperties hibernateProperties) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.edu360.api");
        factory.setPersistenceUnitName("default");

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

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(
            @org.springframework.beans.factory.annotation.Qualifier("entityManagerFactory")
            LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}
