package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
