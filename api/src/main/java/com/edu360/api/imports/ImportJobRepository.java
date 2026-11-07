package com.edu360.api.imports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, ImportJobId> {

    Optional<ImportJob> findByIdTenantIdAndIdImportJobId(UUID tenantId, UUID importJobId);

    List<ImportJob> findByIdTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
