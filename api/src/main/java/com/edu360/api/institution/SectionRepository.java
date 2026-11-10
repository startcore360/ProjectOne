package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    Optional<Section> findByTenantIdAndSectionId(UUID tenantId, UUID sectionId);

    List<Section> findByTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);

    List<Section> findByTenantIdAndInstitutionIdAndSemesterId(UUID tenantId, UUID institutionId, UUID semesterId);

    List<Section> findByTenantIdAndInstitutionIdAndClassId(UUID tenantId, UUID institutionId, UUID classId);

    List<Section> findByTenantIdAndInstitutionIdAndAcademicSessionId(UUID tenantId, UUID institutionId, UUID academicSessionId);

    boolean existsByTenantIdAndAcademicSessionIdAndSemesterIdAndSectionName(UUID tenantId, UUID academicSessionId, UUID semesterId, String sectionName);

    boolean existsByTenantIdAndAcademicSessionIdAndClassIdAndSectionName(UUID tenantId, UUID academicSessionId, UUID classId, String sectionName);
}
