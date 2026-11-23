package com.edu360.api.termlock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class AcademicTermLockIT {
    @Mock private TermLockRepository termLockRepository;
    @Mock private TermLockEventRepository termLockEventRepository;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;
    @Mock private AuditLogService auditLogService;
    @Mock private JdbcTemplate jdbcTemplate;

    private TermLockService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.set(tenantId, IsolationMode.SHARED, null, "ALL", Set.of(), null, null);
        service = new TermLockService(termLockRepository, termLockEventRepository,
                permissionEvaluatorService, auditLogService);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void delegatesTermLockDecisionToDatabaseGuard() {
        UUID sectionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 22);
        when(jdbcTemplate.queryForObject("SELECT app.is_term_locked(?, ?, ?)", Boolean.class,
                tenantId, sectionId, date)).thenReturn(true);

        assertThat(service.isLocked(tenantId, sectionId, date)).isTrue();
        verify(jdbcTemplate).queryForObject("SELECT app.is_term_locked(?, ?, ?)", Boolean.class,
                tenantId, sectionId, date);
    }

    @Test
    void rejectsCrossTenantLockLookupBeforeDatabaseCall() {
        assertThatThrownBy(() -> service.isLocked(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 11, 22))).isInstanceOf(ResourceNotFoundException.class);
    }
}
