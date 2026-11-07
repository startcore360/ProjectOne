package com.edu360.api.imports;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, ImportRowErrorId> {

    List<ImportRowError> findByIdTenantIdAndImportJobIdOrderByRowNumberAsc(UUID tenantId, UUID importJobId);

    void deleteByIdTenantIdAndImportJobId(UUID tenantId, UUID importJobId);
}
