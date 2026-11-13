package com.edu360.api.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.*;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

class StudentCreationAuthorizationTest {
    @Test
    void userCreatePermissionIsCheckedBeforeAllocatingStudentCode() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        TenantPrincipal actor = new TenantPrincipal(tenantId, actorId, UserType.STAFF, "Registrar", false);
        InstitutionRepository institutions = mock(InstitutionRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        Institution institution = Institution.builder().id(new InstitutionId(tenantId, institutionId))
                .institutionType("COLLEGE").institutionCode("COL").institutionName("College")
                .status("ACTIVE").build();
        when(institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId))
                .thenReturn(Optional.of(institution));
        RbacPrincipal principal = new RbacPrincipal(tenantId, actorId);
        doThrow(new UnauthorizedException("ACTION_NOT_GRANTED"))
                .when(permissions).authorize(principal, "USER_CREATE",
                        RbacScope.institution(institutionId), RbacTarget.NONE);
        InstitutionIdFormatService idFormats = mock(InstitutionIdFormatService.class);

        StudentCreationService service = new StudentCreationService(mock(StudentProfileRepository.class),
                institutions, mock(AcademicSessionRepository.class), mock(SectionRepository.class),
                mock(SchoolClassRepository.class), idFormats, mock(StudentEnrollmentService.class),
                mock(UserAccountService.class), mock(AuditLogService.class), permissions);
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        assertThatThrownBy(() -> service.create(actor, new StudentCreateRequest(
                institutionId, "A Student", null, null, UUID.randomUUID(), UUID.randomUUID(), null)))
                .isInstanceOf(UnauthorizedException.class);

        verify(permissions).authorize(principal, "USER_CREATE", RbacScope.institution(institutionId), RbacTarget.NONE);
        verifyNoInteractions(idFormats);
    }
}
