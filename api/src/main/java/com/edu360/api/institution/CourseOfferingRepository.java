package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, UUID> {

    Optional<CourseOffering> findByTenantIdAndCourseOfferingId(UUID tenantId, UUID courseOfferingId);

    List<CourseOffering> findByTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);

    List<CourseOffering> findByTenantIdAndInstitutionIdAndAcademicSessionId(UUID tenantId, UUID institutionId, UUID academicSessionId);

    boolean existsByTenantIdAndSubjectIdAndAcademicSessionIdAndSemesterId(UUID tenantId, UUID subjectId, UUID academicSessionId, UUID semesterId);

    boolean existsByTenantIdAndSubjectIdAndAcademicSessionIdAndClassId(UUID tenantId, UUID subjectId, UUID academicSessionId, UUID classId);
}
