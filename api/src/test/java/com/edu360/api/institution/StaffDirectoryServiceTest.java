package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;

class StaffDirectoryServiceTest {
    private StaffDirectoryService service() {
        return new StaffDirectoryService(mock(StaffProfileRepository.class),
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                mock(PermissionEvaluatorService.class), mock(AuditLogService.class),
                mock(JdbcTemplate.class));
    }

    @Test
    void profileUpdateRequiresIfMatchBeforeDatabaseAccess() {
        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                mock(PermissionEvaluatorService.class), mock(AuditLogService.class),
                mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.update(null, UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("If-Match is required");
        verifyNoInteractions(profiles);
    }

    @Test
    void assignmentEndRequiresIfMatchBeforeDatabaseAccess() {
        StaffAssignmentRepository assignments = mock(StaffAssignmentRepository.class);
        StaffDirectoryService service = new StaffDirectoryService(mock(StaffProfileRepository.class),
                assignments, mock(StaffServiceRecordRepository.class), mock(StaffDocumentRepository.class),
                mock(InstitutionRepository.class), mock(DepartmentRepository.class),
                mock(InstitutionIdFormatService.class), mock(PermissionEvaluatorService.class),
                mock(AuditLogService.class), mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.endAssignment(null, UUID.randomUUID(), UUID.randomUUID(), "invalid", null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("numeric ETag");
        verifyNoInteractions(assignments);
    }
}
