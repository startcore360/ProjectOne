package com.edu360.api.institution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramYearRepository extends JpaRepository<ProgramYear, UUID> {

    Optional<ProgramYear> findByTenantIdAndProgramYearId(UUID tenantId, UUID programYearId);
}
