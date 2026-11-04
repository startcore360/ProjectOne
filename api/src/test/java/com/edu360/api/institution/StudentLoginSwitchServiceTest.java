package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.StudentLoginSwitchRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@ExtendWith(MockitoExtension.class)
class StudentLoginSwitchServiceTest {

    @Mock private SchoolClassRepository schoolClassRepository;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID CLASS_ID = UUID.randomUUID();

    private final RbacPrincipal principal = new RbacPrincipal(TENANT_ID, USER_ID);

    private StudentLoginSwitchService service() {
        return new StudentLoginSwitchService(schoolClassRepository, permissionEvaluatorService);
    }

    private SchoolClass schoolClass() {
        SchoolClass c = new SchoolClass();
        c.setClassId(CLASS_ID);
        c.setTenantId(TENANT_ID);
        c.setInstitutionId(INSTITUTION_ID);
        c.setClassName("9");
        c.setStudentLoginEnabled(false);
        c.setStatus("ACTIVE");
        return c;
    }

    @Test
    void setStudentLogin_enablesItWhenAuthorized() {
        when(schoolClassRepository.findByTenantIdAndClassId(TENANT_ID, CLASS_ID)).thenReturn(Optional.of(schoolClass()));
        when(schoolClassRepository.save(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service().setStudentLogin(principal, CLASS_ID, new StudentLoginSwitchRequest(true));

        assertThat(response.classId()).isEqualTo(CLASS_ID);
        assertThat(response.studentLoginEnabled()).isTrue();
        org.mockito.Mockito.verify(permissionEvaluatorService).authorize(
                principal, "STUDENT_LOGIN_SWITCH", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);
    }

    @Test
    void setStudentLogin_deniesBeforeSavingWhenEvaluatorDenies() {
        when(schoolClassRepository.findByTenantIdAndClassId(TENANT_ID, CLASS_ID)).thenReturn(Optional.of(schoolClass()));
        org.mockito.Mockito.doThrow(new UnauthorizedException("denied"))
                .when(permissionEvaluatorService)
                .authorize(principal, "STUDENT_LOGIN_SWITCH", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThatThrownBy(() -> service().setStudentLogin(principal, CLASS_ID, new StudentLoginSwitchRequest(true)))
                .isInstanceOf(UnauthorizedException.class);

        org.mockito.Mockito.verify(schoolClassRepository, org.mockito.Mockito.never()).save(any());
    }
}
