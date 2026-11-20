package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;

class InstitutionIdFormatServiceTest {
    @Test
    void acceptsUniqueHrSuppliedStaffCodeAfterNormalization() {
        UUID tenantId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        StaffProfileRepository staff = mock(StaffProfileRepository.class);
        when(staff.existsByIdTenantIdAndStaffCode(tenantId, "HR-2026-01")).thenReturn(false);
        InstitutionIdFormatService service = new InstitutionIdFormatService(mock(JdbcTemplate.class),
                mock(InstitutionRepository.class), mock(PermissionEvaluatorService.class),
                mock(AuditLogService.class), mock(StudentProfileRepository.class), staff);

        assertThat(service.resolveStaffCode(tenantId, institutionId, "  hr-2026-01  ", 2026))
                .isEqualTo("HR-2026-01");
    }

    @Test
    void rejectsDuplicateHrSuppliedStaffCodeWithinTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        StaffProfileRepository staff = mock(StaffProfileRepository.class);
        when(staff.existsByIdTenantIdAndStaffCode(tenantId, "HR-2026-01")).thenReturn(true);
        InstitutionIdFormatService service = new InstitutionIdFormatService(mock(JdbcTemplate.class),
                mock(InstitutionRepository.class), mock(PermissionEvaluatorService.class),
                mock(AuditLogService.class), mock(StudentProfileRepository.class), staff);

        assertThatThrownBy(() -> service.resolveStaffCode(tenantId, institutionId, "hr-2026-01", 2026))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already used in this tenant");
    }

    @Test
    void requiresHrSuppliedStaffCodeWhenNoInstitutionFormatExists() {
        UUID tenantId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StaffProfileRepository staff = mock(StaffProfileRepository.class);
        InstitutionIdFormatService service = new InstitutionIdFormatService(jdbc,
                mock(InstitutionRepository.class), mock(PermissionEvaluatorService.class),
                mock(AuditLogService.class), mock(StudentProfileRepository.class), staff);

        assertThatThrownBy(() -> service.resolveStaffCode(tenantId, institutionId, null, 2026))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Enter a unique staff code");
        verifyNoInteractions(staff);
    }
}
