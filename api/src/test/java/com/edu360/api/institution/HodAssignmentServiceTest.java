package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.HodAssignRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.time.TenantClock;

@ExtendWith(MockitoExtension.class)
class HodAssignmentServiceTest {

    @Mock private DepartmentRepository departmentRepository;
    @Mock private StaffAssignmentRepository staffAssignmentRepository;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID STAFF_ASSIGNMENT_ID = UUID.randomUUID();

    private final RbacPrincipal principal = new RbacPrincipal(TENANT_ID, USER_ID);

    private HodAssignmentService service() {
        return new HodAssignmentService(departmentRepository, staffAssignmentRepository, permissionEvaluatorService);
    }

    private Department department() {
        Department d = new Department();
        d.setDepartmentId(DEPARTMENT_ID);
        d.setTenantId(TENANT_ID);
        d.setInstitutionId(INSTITUTION_ID);
        d.setDepartmentCode("CSE");
        d.setDepartmentName("Computer Science");
        d.setStatus("ACTIVE");
        return d;
    }

    private StaffAssignment staffAssignment(UUID departmentId, String status) {
        StaffAssignment sa = new StaffAssignment();
        sa.setStaffAssignmentId(STAFF_ASSIGNMENT_ID);
        sa.setTenantId(TENANT_ID);
        sa.setStaffProfileId(UUID.randomUUID());
        sa.setInstitutionId(INSTITUTION_ID);
        sa.setDepartmentId(departmentId);
        sa.setDesignation("Associate Professor");
        sa.setStatus(status);
        sa.setEffectiveFrom(LocalDate.now(TenantClock.DEFAULT_ZONE).minusYears(1));
        return sa;
    }

    @Test
    void assignHod_allowsWhenAuthorizedAndTargetIsActiveFacultyOfThisDepartment() {
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPARTMENT_ID))
                .thenReturn(Optional.of(department()));
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(TENANT_ID, STAFF_ASSIGNMENT_ID))
                .thenReturn(Optional.of(staffAssignment(DEPARTMENT_ID, "ACTIVE")));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service().assignHod(principal, DEPARTMENT_ID, new HodAssignRequest(STAFF_ASSIGNMENT_ID));

        assertThat(response.departmentId()).isEqualTo(DEPARTMENT_ID);
        assertThat(response.hodStaffAssignmentId()).isEqualTo(STAFF_ASSIGNMENT_ID);
        verify(permissionEvaluatorService).authorize(
                principal, "HOD_ASSIGN", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);
    }

    @Test
    void assignHod_deniesBeforeTouchingStaffDataWhenEvaluatorDenies() {
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPARTMENT_ID))
                .thenReturn(Optional.of(department()));
        org.mockito.Mockito.doThrow(new UnauthorizedException("denied"))
                .when(permissionEvaluatorService)
                .authorize(principal, "HOD_ASSIGN", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThatThrownBy(() -> service().assignHod(principal, DEPARTMENT_ID, new HodAssignRequest(STAFF_ASSIGNMENT_ID)))
                .isInstanceOf(UnauthorizedException.class);

        verifyNoInteractions(staffAssignmentRepository);
    }

    @Test
    void assignHod_rejectsTargetThatIsNotFacultyOfThisDepartment() {
        UUID otherDepartment = UUID.randomUUID();
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPARTMENT_ID))
                .thenReturn(Optional.of(department()));
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(TENANT_ID, STAFF_ASSIGNMENT_ID))
                .thenReturn(Optional.of(staffAssignment(otherDepartment, "ACTIVE")));

        assertThatThrownBy(() -> service().assignHod(principal, DEPARTMENT_ID, new HodAssignRequest(STAFF_ASSIGNMENT_ID)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("not a faculty member");
    }

    @Test
    void assignHod_rejectsInactiveStaffAssignment() {
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPARTMENT_ID))
                .thenReturn(Optional.of(department()));
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(TENANT_ID, STAFF_ASSIGNMENT_ID))
                .thenReturn(Optional.of(staffAssignment(DEPARTMENT_ID, "ENDED")));

        assertThatThrownBy(() -> service().assignHod(principal, DEPARTMENT_ID, new HodAssignRequest(STAFF_ASSIGNMENT_ID)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("not ACTIVE");
    }

    @Test
    void assignHod_throwsNotFoundWhenDepartmentDoesNotExist() {
        when(departmentRepository.findByTenantIdAndDepartmentId(TENANT_ID, DEPARTMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().assignHod(principal, DEPARTMENT_ID, new HodAssignRequest(STAFF_ASSIGNMENT_ID)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(permissionEvaluatorService, staffAssignmentRepository);
    }
}
