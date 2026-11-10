package com.edu360.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.edu360.api.config.CorrelationContext;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;

/**
 * Unit tests for AuditLogService — verifying resilience and OD-09 audit metadata population.
 */
class AuditLogServiceTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        CorrelationContext.clear();
        SecurityContextHolder.clearContext();
    }

    private AuditLogRepository createRepository(AtomicReference<AuditLog> savedRef, AtomicBoolean throwOnSave) {
        return (AuditLogRepository) Proxy.newProxyInstance(
                AuditLogRepository.class.getClassLoader(),
                new Class<?>[]{AuditLogRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName()) && args != null && args.length > 0) {
                        if (throwOnSave.get()) {
                            throw new DataIntegrityViolationException("audit_logs_event_type_check");
                        }
                        savedRef.set((AuditLog) args[0]);
                        return args[0];
                    }
                    return null;
                }
        );
    }

    @Test
    void record_validEvent_savesSuccessfully() {
        AtomicReference<AuditLog> savedRef = new AtomicReference<>();
        AtomicBoolean throwOnSave = new AtomicBoolean(false);
        AuditLogRepository repo = createRepository(savedRef, throwOnSave);
        AuditLogService service = new AuditLogService(repo);

        service.record(AuditEventType.LOGIN_SUCCESS, 1L, "user@test.edu", "127.0.0.1", "details");

        assertThat(savedRef.get()).isNotNull();
        assertThat(savedRef.get().getEventType()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(savedRef.get().getEmail()).isEqualTo("user@test.edu");
        assertThat(savedRef.get().getCorrelationId()).isNotBlank();
    }

    @Test
    void record_populatesCorrelationAndScopeFromContext() {
        AtomicReference<AuditLog> savedRef = new AtomicReference<>();
        AtomicBoolean throwOnSave = new AtomicBoolean(false);
        AuditLogRepository repo = createRepository(savedRef, throwOnSave);
        AuditLogService service = new AuditLogService(repo);

        UUID tenantId = UUID.randomUUID();
        UUID instId = UUID.randomUUID();
        CorrelationContext.set("test-corr-12345");
        TenantContext.set(tenantId, IsolationMode.SHARED, null, "LIST", Set.of(instId), "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION);

        service.record(AuditEventType.USER_DEACTIVATED, null, "admin@test.edu", "127.0.0.1", "Deactivated user");

        assertThat(savedRef.get()).isNotNull();
        assertThat(savedRef.get().getCorrelationId()).isEqualTo("test-corr-12345");
        assertThat(savedRef.get().getActorRoleCode()).isEqualTo("PRINCIPAL_COLLEGE");
        assertThat(savedRef.get().getScopeType()).isEqualTo("INSTITUTION");
        assertThat(savedRef.get().getInstitutionId()).isEqualTo(instId);
    }

    @Test
    void record_constraintViolation_throwsAuditFailureException() {
        AtomicReference<AuditLog> savedRef = new AtomicReference<>();
        AtomicBoolean throwOnSave = new AtomicBoolean(true);
        AuditLogRepository repo = createRepository(savedRef, throwOnSave);
        AuditLogService service = new AuditLogService(repo);

        assertThatThrownBy(() ->
                service.record(AuditEventType.PROFILE_UPDATED, null, "user@test.edu", null, null))
                .isInstanceOf(AuditFailureException.class);
    }

    // ---------------------------------------------------------------- Q-01: tenant audit store

    private static void signIn(UUID tenantId, UUID userId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, userId, UserType.STAFF, "Tenant User", false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }

    @Test
    void regQ01_tenantUserEvent_isWrittenToTenantAuditStore_notLegacyTable() {
        AtomicReference<AuditLog> legacy = new AtomicReference<>();
        List<TenantAuditEvent> tenantRows = new ArrayList<>();
        AuditLogService service = new AuditLogService(createRepository(legacy, new AtomicBoolean(false)), tenantRows::add);

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID instId = UUID.randomUUID();
        CorrelationContext.set("corr-q01");
        TenantContext.set(tenantId, IsolationMode.SHARED, null, "LIST", Set.of(instId), "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION);
        signIn(tenantId, userId);

        service.record(AuditEventType.ROLE_ASSIGNED, null, "admin@test.edu", "10.0.0.1", "Assigned \"HOD\" role");

        assertThat(legacy.get()).isNull();
        assertThat(tenantRows).hasSize(1);
        TenantAuditEvent row = tenantRows.get(0);
        assertThat(row.tenantId()).isEqualTo(tenantId);
        assertThat(row.actorUserId()).isEqualTo(userId);
        assertThat(row.activeRoleCode()).isEqualTo("PRINCIPAL_COLLEGE");
        assertThat(row.institutionId()).isEqualTo(instId);
        assertThat(row.correlationId()).isEqualTo("corr-q01");
        assertThat(row.operation()).isEqualTo("CREATE");
        assertThat(row.outcome()).isEqualTo("SUCCESS");
        assertThat(row.entityType()).isEqualTo("ROLE_ASSIGNED");
        assertThat(row.payloadHash()).matches("[0-9a-f]{64}");
        assertThat(row.newValuesJson()).contains("\"details\":\"Assigned \\\"HOD\\\" role\"");
    }

    @Test
    void regQ01_principalOfAnotherTenant_neverWritesIntoThatTenantsStore() {
        AtomicReference<AuditLog> legacy = new AtomicReference<>();
        List<TenantAuditEvent> tenantRows = new ArrayList<>();
        AuditLogService service = new AuditLogService(createRepository(legacy, new AtomicBoolean(false)), tenantRows::add);

        TenantContext.set(UUID.randomUUID(), IsolationMode.SHARED, null, "ALL", Set.of(), "UNIVERSITY_ADMIN", ScopeType.TENANT);
        signIn(UUID.randomUUID(), UUID.randomUUID());

        service.record(AuditEventType.USER_DEACTIVATED, null, "admin@test.edu", null, "x");

        assertThat(tenantRows).isEmpty();
        assertThat(legacy.get()).isNotNull();
    }

    @Test
    void regQ01_noActiveRole_isNotForgedIntoTenantStore() {
        AtomicReference<AuditLog> legacy = new AtomicReference<>();
        List<TenantAuditEvent> tenantRows = new ArrayList<>();
        AuditLogService service = new AuditLogService(createRepository(legacy, new AtomicBoolean(false)), tenantRows::add);

        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId, IsolationMode.SHARED, null);
        signIn(tenantId, UUID.randomUUID());

        service.record(AuditEventType.PROFILE_UPDATED, null, "user@test.edu", null, null);

        assertThat(tenantRows).isEmpty();
        assertThat(legacy.get()).isNotNull();
    }

    @Test
    void regQ01_noTenantContext_usesLegacySink() {
        AtomicReference<AuditLog> legacy = new AtomicReference<>();
        List<TenantAuditEvent> tenantRows = new ArrayList<>();
        AuditLogService service = new AuditLogService(createRepository(legacy, new AtomicBoolean(false)), tenantRows::add);

        service.record(AuditEventType.LOGIN_FAILURE, null, "unknown@test.edu", "127.0.0.1", null);

        assertThat(tenantRows).isEmpty();
        assertThat(legacy.get().getEventType()).isEqualTo(AuditEventType.LOGIN_FAILURE);
    }

    @Test
    void regQ01_tenantStoreFailure_throwsAuditFailureException() {
        AuditLogService service = new AuditLogService(createRepository(new AtomicReference<>(), new AtomicBoolean(false)),
                event -> { throw new IllegalStateException("new row violates row-level security policy"); });
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId, IsolationMode.SHARED, null, "ALL", Set.of(), "UNIVERSITY_ADMIN", ScopeType.TENANT);
        signIn(tenantId, UUID.randomUUID());

        assertThatThrownBy(() -> service.record(AuditEventType.CREATE, null, "a@test.edu", null, null))
                .isInstanceOf(AuditFailureException.class);
    }

    @Test
    void regQ01_everyEventTypeMapsToD4OperationAndOutcomeLists() {
        Set<String> operations = Set.of("CREATE", "UPDATE", "DELETE", "APPROVE", "REJECT", "LOGIN", "EXPORT",
                "DOWNLOAD", "DENIED", "VIEW_SENSITIVE", "CONFIG_CHANGE");
        Set<String> outcomes = Set.of("SUCCESS", "DENIED", "FAILED");
        for (AuditEventType type : AuditEventType.values()) {
            assertThat(operations).contains(AuditLogService.operationOf(type));
            assertThat(outcomes).contains(AuditLogService.outcomeOf(type));
            assertThat(type.name().length()).isBetween(1, 60); // ck_audit_outbox_entity_type
        }
        assertThat(AuditLogService.operationOf(AuditEventType.LOGIN_FAILURE)).isEqualTo("LOGIN");
        assertThat(AuditLogService.outcomeOf(AuditEventType.LOGIN_FAILURE)).isEqualTo("FAILED");
        assertThat(AuditLogService.operationOf(AuditEventType.DENIED_ATTEMPT)).isEqualTo("DENIED");
        assertThat(AuditLogService.outcomeOf(AuditEventType.DENIED_ATTEMPT)).isEqualTo("DENIED");
        assertThat(AuditLogService.operationOf(AuditEventType.GUARDIAN_CHANGE_APPROVED)).isEqualTo("APPROVE");
        assertThat(AuditLogService.operationOf(AuditEventType.INSTITUTION_BLOCK_REJECTED)).isEqualTo("REJECT");
    }

    @Test
    void record_sessionRevokedConstraintViolation_throwsAuditFailureException() {
        AtomicReference<AuditLog> savedRef = new AtomicReference<>();
        AtomicBoolean throwOnSave = new AtomicBoolean(true);
        AuditLogRepository repo = createRepository(savedRef, throwOnSave);
        AuditLogService service = new AuditLogService(repo);

        assertThatThrownBy(() ->
                service.record(AuditEventType.SESSION_REVOKED, null, "user@test.edu", null, null))
                .isInstanceOf(AuditFailureException.class);
    }
}
