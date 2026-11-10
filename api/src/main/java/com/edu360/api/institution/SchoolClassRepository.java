package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {

    Optional<SchoolClass> findByTenantIdAndClassId(UUID tenantId, UUID classId);

    List<SchoolClass> findByTenantIdAndInstitutionIdOrderByDisplayOrderAsc(UUID tenantId, UUID institutionId);

    boolean existsByTenantIdAndInstitutionIdAndClassName(UUID tenantId, UUID institutionId, String className);
}
