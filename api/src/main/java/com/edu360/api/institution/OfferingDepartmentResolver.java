package com.edu360.api.institution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * A-05: finds the department a course offering belongs to, by following the schema's own
 * foreign keys: course_offering.semester_id -> semester.program_year_id ->
 * program_year.program_id -> program.department_id. A school offering (class_id, no semester)
 * has no department and returns empty. Used to check department-scoped actions such as an HOD's
 * SUBJECT_ALLOCATE at the target's own department.
 */
@Component
@RequiredArgsConstructor
public class OfferingDepartmentResolver {

    private final CourseOfferingRepository courseOfferingRepository;
    private final SemesterRepository semesterRepository;
    private final ProgramYearRepository programYearRepository;
    private final ProgramRepository programRepository;

    public Optional<UUID> departmentOf(UUID tenantId, UUID courseOfferingId) {
        if (tenantId == null || courseOfferingId == null) {
            return Optional.empty();
        }
        return courseOfferingRepository.findByTenantIdAndCourseOfferingId(tenantId, courseOfferingId)
                .map(CourseOffering::getSemesterId)
                .flatMap(semesterId -> semesterRepository.findByTenantIdAndSemesterId(tenantId, semesterId))
                .map(Semester::getProgramYearId)
                .flatMap(yearId -> programYearRepository.findByTenantIdAndProgramYearId(tenantId, yearId))
                .map(ProgramYear::getProgramId)
                .flatMap(programId -> programRepository.findByTenantIdAndProgramId(tenantId, programId))
                .map(Program::getDepartmentId);
    }
}
