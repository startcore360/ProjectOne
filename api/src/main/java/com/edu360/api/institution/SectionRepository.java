package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SectionRepository extends JpaRepository<Section, UUID> {
    Optional<Section> findByTenantIdAndSectionId(UUID tenantId, UUID sectionId);
    List<Section> findByTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);
    List<Section> findByTenantIdAndInstitutionIdAndSemesterId(UUID tenantId, UUID institutionId, UUID semesterId);
    List<Section> findByTenantIdAndInstitutionIdAndClassId(UUID tenantId, UUID institutionId, UUID classId);
    List<Section> findByTenantIdAndInstitutionIdAndAcademicSessionId(UUID tenantId, UUID institutionId, UUID academicSessionId);
    boolean existsByTenantIdAndAcademicSessionIdAndSemesterIdAndSectionName(UUID tenantId, UUID academicSessionId, UUID semesterId, String sectionName);
    boolean existsByTenantIdAndAcademicSessionIdAndClassIdAndSectionName(UUID tenantId, UUID academicSessionId, UUID classId, String sectionName);
    boolean existsByTenantIdAndAcademicSessionIdAndSemesterIdAndSectionNameAndSectionIdNot(UUID tenantId, UUID academicSessionId, UUID semesterId, String sectionName, UUID sectionId);
    boolean existsByTenantIdAndAcademicSessionIdAndClassIdAndSectionNameAndSectionIdNot(UUID tenantId, UUID academicSessionId, UUID classId, String sectionName, UUID sectionId);
    boolean existsByTenantIdAndClassIdAndStatus(UUID tenantId, UUID classId, String status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.section SET updated_by=:updatedBy
             WHERE tenant_id=:tenantId AND section_id=:sectionId AND row_version=:expectedVersion
            """, nativeQuery = true)
    int bumpRowVersion(@Param("tenantId") UUID tenantId, @Param("sectionId") UUID sectionId,
            @Param("expectedVersion") int expectedVersion, @Param("updatedBy") UUID updatedBy);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.section
               SET academic_session_id=:academicSessionId, semester_id=:semesterId, class_id=:classId,
                   section_name=:sectionName, capacity=:capacity, updated_by=:updatedBy
             WHERE tenant_id=:tenantId AND section_id=:sectionId AND row_version=:expectedVersion
            """, nativeQuery = true)
    int updateProfile(@Param("tenantId") UUID tenantId, @Param("sectionId") UUID sectionId,
            @Param("expectedVersion") int expectedVersion, @Param("academicSessionId") UUID academicSessionId,
            @Param("semesterId") UUID semesterId, @Param("classId") UUID classId,
            @Param("sectionName") String sectionName, @Param("capacity") Integer capacity,
            @Param("updatedBy") UUID updatedBy);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.section SET status='ARCHIVED', updated_by=:updatedBy
             WHERE tenant_id=:tenantId AND section_id=:sectionId AND row_version=:expectedVersion
            """, nativeQuery = true)
    int archive(@Param("tenantId") UUID tenantId, @Param("sectionId") UUID sectionId,
            @Param("expectedVersion") int expectedVersion, @Param("updatedBy") UUID updatedBy);
}
