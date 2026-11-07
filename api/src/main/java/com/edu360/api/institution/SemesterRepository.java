package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterRepository extends JpaRepository<Semester, UUID> {

    Optional<Semester> findByTenantIdAndSemesterId(UUID tenantId, UUID semesterId);

    List<Semester> findByTenantIdAndProgramYearIdOrderBySemesterNumberAsc(UUID tenantId, UUID programYearId);

    boolean existsByTenantIdAndProgramYearIdAndSemesterNumber(UUID tenantId, UUID programYearId, Short semesterNumber);
}
