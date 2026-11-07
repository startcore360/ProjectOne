package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramYearRepository extends JpaRepository<ProgramYear, UUID> {

    Optional<ProgramYear> findByTenantIdAndProgramYearId(UUID tenantId, UUID programYearId);

    List<ProgramYear> findByTenantIdAndProgramIdOrderByYearNumberAsc(UUID tenantId, UUID programId);

    boolean existsByTenantIdAndProgramIdAndYearNumber(UUID tenantId, UUID programId, Short yearNumber);
}
