package com.edu360.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Real tests for the bounded-cache/idle-eviction behavior added in Step 8 - no mocking of
 * TenantRoutingDataSource itself, since the whole point is to prove the cache actually stays
 * bounded and actually evicts, not just that the code compiles. HikariDataSource does not
 * attempt a real connection until getConnection() is first called (lazy pool start), so these
 * fake jdbcUrls never need a real database - only determineTargetDataSource()/evictIdlePools()
 * are exercised, never an actual query. determineTargetDataSource() is called directly: it is
 * protected on TenantRoutingDataSource, which same-package test code can call without reflection.
 */
class TenantRoutingDataSourceTest {

    private static final TenantSecretsResolver FAKE_RESOLVER = ref ->
            new TenantDatabaseCredentials("jdbc:postgresql://127.0.0.1:1/fake-" + ref, "u", "p");
    private static final HikariDataSource FAKE_SHARED = new HikariDataSource();

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void cacheStaysWithinBoundEvenWhenMoreDistinctTenantsAreSeen() {
        TenantRoutingDataSource routing = new TenantRoutingDataSource(
                FAKE_SHARED, FAKE_RESOLVER, 2, Duration.ofHours(1));

        for (int i = 0; i < 5; i++) {
            TenantContext.set(UUID.randomUUID(), IsolationMode.ISOLATED, "ref-" + i);
            routing.determineTargetDataSource();
        }

        assertThat(routing.cachedPoolCount())
                .as("cache must never exceed its configured max, even after seeing more distinct tenants")
                .isLessThanOrEqualTo(2);
    }

    @Test
    void idleEvictionClosesAndRemovesStalePools() throws InterruptedException {
        TenantRoutingDataSource routing = new TenantRoutingDataSource(
                FAKE_SHARED, FAKE_RESOLVER, 50, Duration.ofMillis(1));

        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId, IsolationMode.ISOLATED, "ref-idle");
        routing.determineTargetDataSource();
        assertThat(routing.cachedPoolCount()).isEqualTo(1);

        Thread.sleep(10); // let the 1ms idle timeout genuinely elapse
        routing.evictIdlePools();

        assertThat(routing.cachedPoolCount())
                .as("a pool idle longer than idleTimeout must be evicted by the sweep")
                .isZero();
    }

    @Test
    void activeTenantIsNotEvictedWhileStillWithinIdleTimeout() {
        TenantRoutingDataSource routing = new TenantRoutingDataSource(
                FAKE_SHARED, FAKE_RESOLVER, 50, Duration.ofHours(1));

        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId, IsolationMode.ISOLATED, "ref-active");
        routing.determineTargetDataSource();

        routing.evictIdlePools();

        assertThat(routing.cachedPoolCount())
                .as("a pool well within idleTimeout must survive a sweep")
                .isEqualTo(1);
    }

    @Test
    void manualEvictClosesAndRemovesImmediately() {
        TenantRoutingDataSource routing = new TenantRoutingDataSource(
                FAKE_SHARED, FAKE_RESOLVER, 50, Duration.ofHours(1));

        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId, IsolationMode.ISOLATED, "ref-manual");
        routing.determineTargetDataSource();
        assertThat(routing.cachedPoolCount()).isEqualTo(1);

        routing.evict(tenantId, "test cleanup");

        assertThat(routing.cachedPoolCount()).isZero();
    }

    @Test
    void sharedTenantNeverEntersTheIsolatedCacheAtAll() {
        TenantRoutingDataSource routing = new TenantRoutingDataSource(
                FAKE_SHARED, FAKE_RESOLVER, 50, Duration.ofHours(1));

        TenantContext.set(UUID.randomUUID(), IsolationMode.SHARED, null);
        Object target = routing.determineTargetDataSource();

        assertThat(target).isSameAs(FAKE_SHARED);
        assertThat(routing.cachedPoolCount()).isZero();
    }
}
