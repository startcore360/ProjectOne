package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemesterRepository extends JpaRepository<Semester, UUID> {

    Optional<Semester> findByTenantIdAndSemesterId(UUID tenantId, UUID semesterId);

    List<Semester> findByTenantIdAndProgramYearIdOrderBySemesterNumberAsc(UUID tenantId, UUID programYearId);

    boolean existsByTenantIdAndProgramYearIdAndSemesterNumber(UUID tenantId, UUID programYearId, Short semesterNumber);

    @Query("""
            select case when count(s) > 0 then true else false end
            from Semester s
            where s.tenantId = :tenantId
              and s.institutionId = :institutionId
              and s.startDate is not null
              and s.endDate is not null
              and s.startDate <= :endDate
              and s.endDate >= :startDate
            """)
    boolean existsOverlappingInstitutionTerm(
            @Param("tenantId") UUID tenantId,
            @Param("institutionId") UUID institutionId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
