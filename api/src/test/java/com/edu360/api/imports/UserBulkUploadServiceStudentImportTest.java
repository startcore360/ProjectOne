package com.edu360.api.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.files.LocalFileStorage;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.imports.dto.UserImportRequest;

class UserBulkUploadServiceStudentImportTest {
    private UserBulkUploadService service(ImportJobRepository jobs, ImportRowErrorRepository errors,
            LocalFileStorage storage) {
        return new UserBulkUploadService(jobs, errors, mock(FileObjectRepository.class), storage,
                mock(PermissionEvaluatorService.class), mock(UserAccountRepository.class),
                mock(UserBulkImportRowService.class), mock(AuditLogService.class));
    }

    @Test
    void acceptsStudentRowsWithoutEmailWhenSectionAndSessionAreSupplied() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ImportJobRepository jobs = mock(ImportJobRepository.class);
        ImportRowErrorRepository errors = mock(ImportRowErrorRepository.class);
        LocalFileStorage storage = mock(LocalFileStorage.class);
        when(storage.store(eq(tenantId), any(UUID.class), any(InputStream.class))).thenReturn("stored.csv");
        when(jobs.save(any(ImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String csv = "fullName,userType,institutionId,sectionId,academicSessionId,admissionDate,dateOfBirth,rollNumber,createLogin\n"
                + "Asha Rao,STUDENT,," + sectionId + "," + sessionId + ",2026-06-01,,R-01,\n";
        var response = service(jobs, errors, storage).createUserImport(
                new RbacPrincipal(tenantId, actorId), tenantId,
                new UserImportRequest(csv, institutionId, "STUDENTS"));

        assertThat(response.validRows()).isEqualTo(1);
        assertThat(response.errorRows()).isZero();
    }

    @Test
    void rejectsStudentRowsMissingSectionOrSessionDuringPrevalidation() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        ImportJobRepository jobs = mock(ImportJobRepository.class);
        ImportRowErrorRepository errors = mock(ImportRowErrorRepository.class);
        LocalFileStorage storage = mock(LocalFileStorage.class);
        when(storage.store(eq(tenantId), any(UUID.class), any(InputStream.class))).thenReturn("stored.csv");
        when(jobs.save(any(ImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String csv = "fullName,userType,institutionId,sectionId,academicSessionId\nAsha Rao,STUDENT,,,\n";
        var response = service(jobs, errors, storage).createUserImport(
                new RbacPrincipal(tenantId, actorId), tenantId,
                new UserImportRequest(csv, institutionId, "STUDENTS"));

        assertThat(response.validRows()).isZero();
        assertThat(response.errorRows()).isGreaterThanOrEqualTo(2);
    }
}
