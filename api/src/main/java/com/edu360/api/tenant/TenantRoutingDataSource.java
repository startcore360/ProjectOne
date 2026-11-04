package com.edu360.api.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.scheduling.annotation.Scheduled;

import com.zaxxer.hikari.HikariDataSource;

/**
 * WHAT: the one DataSource every repository in this app actually talks to. Picks, per request,
 * either the shared Postgres pool (SHARED tenants, kept apart by RLS) or a lazily-opened pool
 * for that specific ISOLATED tenant's own database - then, either way, tells Postgres which
 * tenant (app.tenant_id) AND which institutions within that tenant (app.institution_scope /
 * app.institution_ids - the OD-03 institution-level RLS layer under tenant RLS) this connection
 * is allowed to see before handing the connection back to Hibernate. See applyTenantGucs(...)
 * below for exactly what is set and why.
 *
 * HOW routing decides SHARED vs ISOLATED: entirely from TenantContext, which the JWT converter
 * (TenantIssuerAuthenticationManagerResolver) populates only after a token is fully verified.
 * No tenant context set at all (Platform Admin, or anything running before authentication) ->
 * the shared pool, with app.tenant_id deliberately left unset, so RLS denies every app.* row.
 *
 * WHY ISOLATED pools are opened lazily and cached here instead of upfront at startup: tenants
 * are created and activated over time (TENANT_CREATE/TENANT_ACTIVATE, a separate Platform-epic
 * story), so there is no fixed list of databases to connect to when this app boots. Each pool
 * is opened once, the first time that tenant is ever seen, and reused after that.
 *
 * BOUNDED CACHE + EVICTION: isolatedPools is capped at maxCachedPools entries and sweeps idle
 * entries every evictionCheckInterval via evictIdlePools(). An entry idle longer than
 * idleTimeout gets its HikariDataSource closed (releasing its real DB connections, not just
 * forgotten) and removed. If the map is still over the cap after an idle sweep (many tenants
 * genuinely active at once), the single least-recently-used entry is evicted too, so the cache
 * never grows without bound regardless of traffic pattern.
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantRoutingDataSource.class);

    private final DataSource sharedDataSource;
    private final TenantSecretsResolver secretsResolver;
    private final Map<UUID, CachedPool> isolatedPools = new ConcurrentHashMap<>();
    private final int maxCachedPools;
    private final Duration idleTimeout;

    /** Pairs a pool with when it was last actually borrowed from, so the eviction sweep below
     *  can tell an idle pool from a busy one without touching Hikari's own internals. */
    private static final class CachedPool {
        final HikariDataSource dataSource;
        final AtomicReference<Instant> lastUsedAt;

        CachedPool(HikariDataSource dataSource) {
            this.dataSource = dataSource;
            this.lastUsedAt = new AtomicReference<>(Instant.now());
        }
    }

    public TenantRoutingDataSource(DataSource sharedDataSource, TenantSecretsResolver secretsResolver) {
        this(sharedDataSource, secretsResolver, 50, Duration.ofMinutes(30));
    }

    public TenantRoutingDataSource(DataSource sharedDataSource, TenantSecretsResolver secretsResolver,
                                    int maxCachedPools, Duration idleTimeout) {
        this.sharedDataSource = sharedDataSource;
        this.secretsResolver = secretsResolver;
        this.maxCachedPools = maxCachedPools;
        this.idleTimeout = idleTimeout;
        // Required by AbstractRoutingDataSource.afterPropertiesSet() even though
        // determineTargetDataSource() below is fully overridden and never actually consults
        // this map - an empty map plus a default satisfies that base-class check cleanly.
        setTargetDataSources(new HashMap<>());
        setDefaultTargetDataSource(sharedDataSource);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        // Only used for AbstractRoutingDataSource's own logging/diagnostics, since
        // determineTargetDataSource() below is fully overridden - but returning the real
        // current tenant (or null) keeps that diagnostic output meaningful instead of useless.
        return TenantContext.currentTenantId();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        UUID tenantId = TenantContext.currentTenantId();
        if (tenantId == null) {
            return sharedDataSource;
        }
        IsolationMode mode = TenantContext.currentIsolationMode();
        if (mode != IsolationMode.ISOLATED) {
            return sharedDataSource;
        }
        CachedPool cached = isolatedPools.computeIfAbsent(tenantId, id -> new CachedPool(openIsolatedPool()));
        cached.lastUsedAt.set(Instant.now());
        enforceBoundAfterInsert();
        return cached.dataSource;
    }

    private HikariDataSource openIsolatedPool() {
        String dbConnectionRef = TenantContext.currentDbConnectionRef();
        if (dbConnectionRef == null) {
            // Fail closed: an ISOLATED tenant with no connection reference is a data
            // inconsistency (the DB CHECK constraint ck_tenant_r2 should prevent this ever
            // being saved), never something to silently fall back to the shared pool for -
            // that would put this tenant's queries into the wrong database entirely.
            throw new IllegalStateException(
                    "Tenant is ISOLATED but has no db_connection_ref - refusing to guess a database");
        }
        TenantDatabaseCredentials credentials = secretsResolver.resolve(dbConnectionRef);
        HikariDataSource pool = new HikariDataSource();
        pool.setJdbcUrl(credentials.jdbcUrl());
        pool.setUsername(credentials.username());
        pool.setPassword(credentials.password());
        return pool;
    }

    /** Called right after a possible insert, so the cache never exceeds its cap even under
     *  heavy concurrent traffic across many distinct ISOLATED tenants between scheduled sweeps -
     *  evicts only the single least-recently-used entry, not a batch, since this runs on the hot
     *  request path and must stay cheap. */
    private void enforceBoundAfterInsert() {
        if (isolatedPools.size() <= maxCachedPools) {
            return;
        }
        isolatedPools.entrySet().stream()
                .min((a, b) -> a.getValue().lastUsedAt.get().compareTo(b.getValue().lastUsedAt.get()))
                .ifPresent(entry -> evict(entry.getKey(), "cache over capacity (" + maxCachedPools + ")"));
    }

    /** Runs every 5 minutes; closes and removes any ISOLATED-tenant pool that has not been
     *  borrowed from in longer than idleTimeout. fixedDelay (not fixedRate): if a sweep ever
     *  runs long, the next one waits for it to finish instead of piling up concurrent sweeps. */
    @Scheduled(fixedDelay = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void evictIdlePools() {
        Instant cutoff = Instant.now().minus(idleTimeout);
        isolatedPools.entrySet().stream()
                .filter(entry -> entry.getValue().lastUsedAt.get().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(tenantId -> evict(tenantId, "idle longer than " + idleTimeout));
    }

    /** Closes the pool's real connections before dropping it - removing it from the map alone
     *  would leak the HikariDataSource's background threads and open sockets. Also the method
     *  tenant cleanup (e.g. a tenant moving off ISOLATED mode, or being suspended/archived) can
     *  call directly, rather than waiting out the idle timeout. */
    synchronized void evict(UUID tenantId, String reason) {
        CachedPool removed = isolatedPools.remove(tenantId);
        if (removed != null) {
            log.info("Closing ISOLATED pool for tenant {} ({})", tenantId, reason);
            removed.dataSource.close();
        }
    }

    int cachedPoolCount() {
        return isolatedPools.size();
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        applyTenantGucs(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        applyTenantGucs(connection);
        return connection;
    }

    /**
     * Tells Postgres, on this specific connection, which tenant AND which institution scope the
     * current request is allowed to see - this is what sql/03_bindings.sql's RLS policies
     * actually read via current_setting('app.tenant_id'/'app.institution_scope'/
     * 'app.institution_ids'). Package-private (not private) so
     * TenantRoutingDataSourceInstitutionScopeIT can exercise it directly against a real
     * connection without going through the full getConnection()/Hikari path - the same reason
     * cachedPoolCount()/evict(...) below are package-private rather than private.
     *
     * HOW/WHY session-level SET, not SET LOCAL (found by actually logging in, not by review):
     * SET LOCAL only lasts for the current transaction, but this method runs at connection-
     * CHECKOUT time (inside getConnection()), which happens BEFORE Hibernate begins its own
     * transaction on that connection - so a SET LOCAL issued here runs as its own separate
     * autocommit statement, takes effect for a transaction that immediately ends, and is gone
     * before the real query that follows ever runs. Plain SET is session-scoped (survives until
     * changed again or the connection closes) and is reapplied on every single borrow here
     * regardless of tenant, which is exactly what keeps pooled-connection reuse safe: a
     * connection previously used for tenant A is explicitly reset (to tenant B's value, or
     * fully cleared below) the moment it is borrowed again - the GUC is never left over from
     * whoever used this physical connection last. The same discipline applies to
     * app.institution_ids below: it is ALWAYS explicitly SET (to the real CSV list, or to an
     * empty string) on every borrow, never just RESET-or-skipped on one path and left untouched
     * on another - a connection previously borrowed for a request scoped to institution X must
     * never carry institution X's id into a later borrow for a request with no institution
     * access at all.
     *
     * WHY string concatenation here is safe (not a SQL-injection risk) even though Postgres's
     * SET command does not accept JDBC bind parameters at all: tenantId and every element of
     * institutionIds are java.util.UUID, whose toString() is always exactly 36 fixed
     * hex-and-hyphen characters - structurally incapable of containing a quote character to
     * break out of the literal.
     */
    void applyTenantGucs(Connection connection) throws SQLException {
        UUID tenantId = TenantContext.currentTenantId();
        try (Statement statement = connection.createStatement()) {
            if (tenantId == null) {
                // RESET, not skip: this physical connection may have been used for a DIFFERENT
                // tenant on its previous borrow (Hikari pools and reuses connections) - without
                // an explicit reset here, that earlier tenant's GUC value would still be set on
                // this connection, which is exactly the cross-tenant leak TenantContext's class
                // comment warns about. RESET clears it back to unset, which is what keeps RLS
                // closed for this borrow (Platform Admin, or anything pre-authentication).
                statement.execute("RESET app.tenant_id");
                statement.execute("RESET app.institution_scope");
                statement.execute("RESET app.institution_ids");
                return;
            }
            statement.execute("SET app.tenant_id = '" + tenantId + "'");
            // Foundation Item 7 (the 71-role/280-action model) is what will compute a real
            // institution scope from the caller's active role assignment. Until that exists,
            // NONE is the fail-closed default: sql/03_bindings.sql's restrictive p_institution
            // policy treats "scope = NONE and no institution id matches" as "deny every
            // institution-scoped row" - never "allow everything" by default.
            statement.execute("SET app.institution_scope = 'NONE'");
            statement.execute(institutionIdsSetStatement());
        }
    }

    /** Builds the SET app.institution_ids statement from TenantContext.currentInstitutionIds() -
     *  an empty CSV string when the caller has no institution scope (TenantContext's fail-closed
     *  default, see its class comment's INSTITUTION SCOPE section), never a bare RESET, so this
     *  GUC is always explicitly overwritten on every borrow regardless of what the previous
     *  occupant of this pooled connection left behind. */
    private String institutionIdsSetStatement() {
        Set<UUID> institutionIds = TenantContext.currentInstitutionIds();
        if (institutionIds.isEmpty()) {
            return "SET app.institution_ids = ''";
        }
        String csv = institutionIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        return "SET app.institution_ids = '" + csv + "'";
    }
}
