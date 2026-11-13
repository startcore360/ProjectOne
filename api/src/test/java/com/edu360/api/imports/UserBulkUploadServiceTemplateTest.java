package com.edu360.api.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.files.LocalFileStorage;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;

class UserBulkUploadServiceTemplateTest {
    private UserBulkUploadService service() {
        return new UserBulkUploadService(
                mock(ImportJobRepository.class),
                mock(ImportRowErrorRepository.class),
                mock(FileObjectRepository.class),
                mock(LocalFileStorage.class),
                mock(PermissionEvaluatorService.class),
                mock(UserAccountRepository.class),
                mock(UserBulkImportRowService.class),
                mock(AuditLogService.class));
    }

    @Test
    void studentTemplateIncludesSectionAndSessionEnrollmentColumnsWithoutEmail() {
        var template = service().getImportTemplate("STUDENTS");

        assertThat(template.headers()).contains("fullName", "userType", "institutionId",
                "sectionId", "academicSessionId", "admissionDate", "dateOfBirth", "rollNumber", "createLogin");
        assertThat(template.headers()).doesNotContain("email");
    }

    @Test
    void genericUserTemplateStillRequiresEmail() {
        var template = service().getImportTemplate("USERS");

        assertThat(template.headers()).contains("email", "fullName", "userType", "roleCode");
    }
}
