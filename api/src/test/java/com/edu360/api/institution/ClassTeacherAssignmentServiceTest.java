package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.institution.dto.ClassTeacherAssignRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.time.TenantClock;

@ExtendWith(MockitoExtension.class)
class ClassTeacherAssignmentServiceTest {

    @Mock private SectionRepository sectionRepository;
    @Mock private StaffAssignmentRepository staffAssignmentRepository;
    @Mock private ClassTeacherAssignmentRepository classTeacherAssignmentRepository;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID SECTION_ID = UUID.randomUUID();
    private static final UUID STAFF_ASSIGNMENT_ID = UUID.randomUUID();

    private final RbacPrincipal principal = new RbacPrincipal(TENANT_ID, USER_ID);

    private ClassTeacherAssignmentService service() {
        return new ClassTeacherAssignmentService(
                sectionRepository, staffAssignmentRepository, classTeacherAssignmentRepository, permissionEvaluatorService);
    }

    private Section schoolSection() {
        Section s = new Section();
        s.setSectionId(SECTION_ID);
        s.setTenantId(TENANT_ID);
        s.setInstitutionId(INSTITUTION_ID);
        s.setAcademicSessionId(UUID.randomUUID());
        s.setClassId(UUID.randomUUID());
        s.setSectionName("A");
        s.setStatus("ACTIVE");
        return s;
    }

    private StaffAssignment staffAssignment(UUID institutionId, String status) {
        StaffAssignment sa = new StaffAssignment();
        sa.setStaffAssignmentId(STAFF_ASSIGNMENT_ID);
        sa.setTenantId(TENANT_ID);
        sa.setInstitutionId(institutionId);
        sa.setDesignation("Teacher");
        sa.setStatus(status);
        sa.setEffectiveFrom(LocalDate.now(TenantClock.DEFAULT_ZONE).minusYears(1));
        return sa;
    }

    @Test
    void assignClassTeacher_allowsWhenAuthorizedAndTargetIsActiveStaffOfThisInstitution() {
        when(sectionRepository.findByTenantIdAndSectionId(TENANT_ID, SECTION_ID)).thenReturn(Optional.of(schoolSection()));
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(TENANT_ID, STAFF_ASSIGNMENT_ID))
                .thenReturn(Optional.of(staffAssignment(INSTITUTION_ID, "ACTIVE")));
        when(classTeacherAssignmentRepository.findByTenantIdAndSectionIdAndEffectiveToIsNull(TENANT_ID, SECTION_ID))
                .thenReturn(Optional.empty());
        when(classTeacherAssignmentRepository.save(any(ClassTeacherAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service().assignClassTeacher(principal, SECTION_ID, new ClassTeacherAssignRequest(STAFF_ASSIGNMENT_ID));

        assertThat(response.sectionId()).isEqualTo(SECTION_ID);
        assertThat(response.staffAssignmentId()).isEqualTo(STAFF_ASSIGNMENT_ID);
        org.mockito.Mockito.verify(permissionEvaluatorService).authorize(
                principal, "CLASS_TEACHER_ASSIGN", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);
    }

    @Test
    void assignClassTeacher_rejectsFutureEffectiveStaffAssignment() {
        when(sectionRepository.findByTenantIdAndSectionId(TENANT_ID, SECTION_ID)).thenReturn(Optional.of(schoolSection()));
        StaffAssignment future = staffAssignment(INSTITUTION_ID, "ACTIVE");
        future.setEffectiveFrom(LocalDate.now(TenantClock.DEFAULT_ZONE).plusDays(1));
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(TENANT_ID, STAFF_ASSIGNMENT_ID))
                .thenReturn(Optional.of(future));

        assertThatThrownBy(() -> service().assignClassTeacher(principal, SECTION_ID, new ClassTeacherAssignRequest(STAFF_ASSIGNMENT_ID)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not effective");
    }

    @Test
    void assignClassTeacher_endsTheExistingSectionAssignmentBeforeReplacement() {
        when(sectionRepository.findByTenantIdAndSectionId(TENANT_ID, SECTION_ID)).thenReturn(Optional.of(schoolSection()));
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(TENANT_ID, STAFF_ASSIGNMENT_ID))
                .thenReturn(Optional.of(staffAssignment(INSTITUTION_ID, "ACTIVE")));
        ClassTeacherAssignment current = new ClassTeacherAssignment();
        current.setClassTeacherAssignmentId(UUID.randomUUID());
        current.setTenantId(TENANT_ID);
        current.setInstitutionId(INSTITUTION_ID);
        current.setSectionId(SECTION_ID);
        current.setStaffAssignmentId(UUID.randomUUID());
        current.setEffectiveFrom(LocalDate.now(TenantClock.DEFAULT_ZONE).minusMonths(3));
        when(classTeacherAssignmentRepository.findByTenantIdAndSectionIdAndEffectiveToIsNull(TENANT_ID, SECTION_ID))
                .thenReturn(Optional.of(current));
        when(classTeacherAssignmentRepository.save(any(ClassTeacherAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        service().assignClassTeacher(principal, SECTION_ID, new ClassTeacherAssignRequest(STAFF_ASSIGNMENT_ID));

        assertThat(current.getEffectiveTo()).isEqualTo(LocalDate.now(TenantClock.DEFAULT_ZONE));
    }

    @Test
    void assignClassTeacher_deniesUnauthorizedCallerBeforeReadingStaffAssignment() {
        when(sectionRepository.findByTenantIdAndSectionId(TENANT_ID, SECTION_ID)).thenReturn(Optional.of(schoolSection()));
        org.mockito.Mockito.doThrow(new UnauthorizedException("denied"))
                .when(permissionEvaluatorService)
                .authorize(principal, "CLASS_TEACHER_ASSIGN", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        assertThatThrownBy(() -> service().assignClassTeacher(principal, SECTION_ID, new ClassTeacherAssignRequest(STAFF_ASSIGNMENT_ID)))
                .isInstanceOf(UnauthorizedException.class);

        org.mockito.Mockito.verifyNoInteractions(staffAssignmentRepository, classTeacherAssignmentRepository);
    }

    @Test
    void assignClassTeacher_rejectsACollegeSectionBeforeAnyStaffLookup() {
        Section collegeSection = schoolSection();
        collegeSection.setClassId(null);
        collegeSection.setSemesterId(UUID.randomUUID());
        when(sectionRepository.findByTenantIdAndSectionId(TENANT_ID, SECTION_ID)).thenReturn(Optional.of(collegeSection));

        assertThatThrownBy(() -> service().assignClassTeacher(principal, SECTION_ID, new ClassTeacherAssignRequest(STAFF_ASSIGNMENT_ID)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("college section");

        org.mockito.Mockito.verifyNoInteractions(permissionEvaluatorService, staffAssignmentRepository);
    }

    @Test
    void assignClassTeacher_rejectsTargetFromADifferentInstitution() {
        when(sectionRepository.findByTenantIdAndSectionId(TENANT_ID, SECTION_ID)).thenReturn(Optional.of(schoolSection()));
        UUID otherInstitution = UUID.randomUUID();
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(TENANT_ID, STAFF_ASSIGNMENT_ID))
                .thenReturn(Optional.of(staffAssignment(otherInstitution, "ACTIVE")));

        assertThatThrownBy(() -> service().assignClassTeacher(principal, SECTION_ID, new ClassTeacherAssignRequest(STAFF_ASSIGNMENT_ID)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("does not belong to institution");
    }
}
