package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicSessionRepository extends JpaRepository<AcademicSession, UUID> {

    List<AcademicSession> findByTenantIdOrderByAcademicYearDesc(UUID tenantId);

    List<AcademicSession> findByTenantIdAndInstitutionIdOrderByAcademicYearDesc(UUID tenantId, UUID institutionId);

    Optional<AcademicSession> findByTenantIdAndAcademicSessionId(UUID tenantId, UUID academicSessionId);

    List<AcademicSession> findByTenantIdAndInstitutionIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID tenantId, UUID institutionId, LocalDate startDate, LocalDate endDate);

    List<AcademicSession> findByTenantIdAndInstitutionIdIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID tenantId, LocalDate startDate, LocalDate endDate);

    boolean existsByTenantIdAndInstitutionIdAndSessionLabel(UUID tenantId, UUID institutionId, String sessionLabel);

    boolean existsByTenantIdAndInstitutionIdIsNullAndSessionLabel(UUID tenantId, String sessionLabel);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE app.academic_session target SET is_current = false, updated_by = :actorId " +
            "WHERE target.tenant_id = :tenantId AND target.institution_id IS NOT DISTINCT FROM " +
            "(SELECT selected.institution_id FROM app.academic_session selected WHERE selected.tenant_id = :tenantId AND selected.academic_session_id = :sessionId) " +
            "AND target.is_current = true", nativeQuery = true)
    int clearCurrentInScope(@Param("tenantId") UUID tenantId, @Param("sessionId") UUID sessionId, @Param("actorId") UUID actorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE app.academic_session SET is_current = true, updated_by = :actorId WHERE tenant_id = :tenantId AND academic_session_id = :sessionId", nativeQuery = true)
    int setCurrentById(@Param("tenantId") UUID tenantId, @Param("sessionId") UUID sessionId, @Param("actorId") UUID actorId);
}
