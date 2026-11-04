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
import com.edu360.api.institution.dto.MentorAssignRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@ExtendWith(MockitoExtension.class)
class MentorAssignmentServiceTest {

    @Mock private SectionRepository sectionRepository;
    @Mock private SemesterRepository semesterRepository;
    @Mock private ProgramYearRepository programYearRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private StaffAssignmentRepository staffAssignmentRepository;
    @Mock private MentorAssignmentRepository mentorAssignmentRepository;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID SECTION_ID = UUID.randomUUID();
    private static final UUID SEMESTER_ID = UUID.randomUUID();
    private static final UUID PROGRAM_YEAR_ID = UUID.randomUUID();
    private static final UUID PROGRAM_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID STAFF_ASSIGNMENT_ID = UUID.randomUUID();

    private final RbacPrincipal principal = new RbacPrincipal(TENANT_ID, USER_ID);

    private MentorAssignmentService service() {
        return new MentorAssignmentService(sectionRepository, semesterRepository, programYearRepository,
                programRepository, staffAssignmentRepository, mentorAssignmentRepository, permissionEvaluatorService);
    }

    private Section collegeSection() {
        Section s = new Section();
        s.setSectionId(SECTION_ID);
        s.setTenantId(TENANT_ID);
        s.setInstitutionId(INSTITUTION_ID);
        s.setAcademicSessionId(UUID.randomUUID());
        s.setSemesterId(SEMESTER_ID);
        s.setSectionName("A");
        s.setStatus("ACTIVE");
        return s;
    }

    private void stubDepartmentChain() {
        Semester semester = new Semester();
        semester.setSemesterId(SEMESTER_ID);
        semester.setTenantId(TENANT_ID);
        semester.setProgramYearId(PROGRAM_YEAR_ID);
        when(semesterRepository.findByTenantIdAndSemesterId(TENANT_ID, SEMESTER_ID)).thenReturn(Optional.of(semester));

        ProgramYear programYear = new ProgramYear();
        programYear.setProgramYearId(PROGRAM_YEAR_ID);
        programYear.setTenantId(TENANT_ID);
        programYear.setProgramId(PROGRAM_ID);
        when(programYearRepository.findByTenantIdAndProgramYearId(TENANT_ID, PROGRAM_YEAR_ID))
                .thenReturn(Optional.of(programYear));

        Program program = new Program();
        program.setProgramId(PROGRAM_ID);
        program.setTenantId(TENANT_ID);
        program.setDepartmentId(DEPARTMENT_ID);
        when(programRepository.findByTenantIdAndProgramId(TENANT_ID, PROGRAM_ID)).thenReturn(Optional.of(program));
    }

    private StaffAssignment staffAssignment(UUID departmentId, String status) {
        StaffAssignment sa = new StaffAssignment();
        sa.setStaffAssignmentId(STAFF_ASSIGNMENT_ID);
        sa.setTenantId(TENANT_ID);
        sa.setInstitutionId(INSTITUTION_ID);
        sa.setDepartmentId(departmentId);
        sa.setDesignation("Assistant Professor");
        sa.setStatus(status);
        return sa;
    }

    @Test
    void assignMentor_allowsAndEndsThePriorMentorWhenTargetIsActiveFacultyOfTheSectionDepartment() {
        when(sectionRepository.findByTenantIdAndSectionId(TENANT_ID, SECTION_ID)).thenReturn(Optional.of(collegeSection()));
        stubDepartmentChain();
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(TENANT_ID, STAFF_ASSIGNMENT_ID))
                .thenReturn(Optional.of(staffAssignment(DEPARTMENT_ID, "ACTIVE")));

        MentorAssignment current = new MentorAssignment();
        current.setMentorAssignmentId(UUID.randomUUID());
        current.setSectionId(SECTION_ID);
        current.setEffectiveFrom(LocalDate.now().minusMonths(6));
        when(mentorAssignmentRepository.findByTenantIdAndSectionIdAndEffectiveToIsNull(TENANT_ID, SECTION_ID))
                .thenReturn(Optional.of(current));
        when(mentorAssignmentRepository.save(any(MentorAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service().assignMentor(principal, SECTION_ID, new MentorAssignRequest(STAFF_ASSIGNMENT_ID));

        assertThat(response.sectionId()).isEqualTo(SECTION_ID);
        assertThat(response.staffAssignmentId()).isEqualTo(STAFF_ASSIGNMENT_ID);
        assertThat(current.getEffectiveTo()).isEqualTo(LocalDate.now());
        org.mockito.Mockito.verify(permissionEvaluatorService).authorize(
                principal, "MENTOR_ASSIGN", RbacScope.department(INSTITUTION_ID, DEPARTMENT_ID), RbacTarget.NONE);
    }

    @Test
    void assignMentor_rejectsASchoolSectionBeforeAnyDepartmentLookup() {
        Section schoolSection = collegeSection();
        schoolSection.setSemesterId(null);
        schoolSection.setClassId(UUID.randomUUID());
        when(sectionRepository.findByTenantIdAndSectionId(TENANT_ID, SECTION_ID)).thenReturn(Optional.of(schoolSection));

        assertThatThrownBy(() -> service().assignMentor(principal, SECTION_ID, new MentorAssignRequest(STAFF_ASSIGNMENT_ID)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("school section");

        org.mockito.Mockito.verifyNoInteractions(semesterRepository, permissionEvaluatorService);
    }

    @Test
    void assignMentor_rejectsTargetThatIsNotFacultyOfTheSectionDepartment() {
        when(sectionRepository.findByTenantIdAndSectionId(TENANT_ID, SECTION_ID)).thenReturn(Optional.of(collegeSection()));
        stubDepartmentChain();
        UUID otherDepartment = UUID.randomUUID();
        when(staffAssignmentRepository.findByTenantIdAndStaffAssignmentId(TENANT_ID, STAFF_ASSIGNMENT_ID))
                .thenReturn(Optional.of(staffAssignment(otherDepartment, "ACTIVE")));

        assertThatThrownBy(() -> service().assignMentor(principal, SECTION_ID, new MentorAssignRequest(STAFF_ASSIGNMENT_ID)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("not a faculty member");
    }
}
