package com.edu360.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.edu360.api.config.CorrelationContext;
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
    void record_constraintViolation_doesNotThrow() {
        AtomicReference<AuditLog> savedRef = new AtomicReference<>();
        AtomicBoolean throwOnSave = new AtomicBoolean(true);
        AuditLogRepository repo = createRepository(savedRef, throwOnSave);
        AuditLogService service = new AuditLogService(repo);

        assertThatCode(() ->
                service.record(AuditEventType.PROFILE_UPDATED, null, "user@test.edu", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void record_sessionRevokedConstraintViolation_doesNotThrow() {
        AtomicReference<AuditLog> savedRef = new AtomicReference<>();
        AtomicBoolean throwOnSave = new AtomicBoolean(true);
        AuditLogRepository repo = createRepository(savedRef, throwOnSave);
        AuditLogService service = new AuditLogService(repo);

        assertThatCode(() ->
                service.record(AuditEventType.SESSION_REVOKED, null, "user@test.edu", null, null))
                .doesNotThrowAnyException();
    }
}
