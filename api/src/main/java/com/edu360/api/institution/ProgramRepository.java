package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, UUID> {

    Optional<Program> findByTenantIdAndProgramId(UUID tenantId, UUID programId);

    List<Program> findByTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);

    boolean existsByTenantIdAndInstitutionIdAndProgramCode(UUID tenantId, UUID institutionId, String programCode);
}
