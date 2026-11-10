package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstitutionRepository extends JpaRepository<Institution, InstitutionId> {

    List<Institution> findByIdTenantId(UUID tenantId);

    List<Institution> findByIdTenantIdAndStatusIn(UUID tenantId, List<String> statuses);

    Optional<Institution> findByIdTenantIdAndIdInstitutionId(UUID tenantId, UUID institutionId);

    boolean existsByIdTenantIdAndInstitutionCode(UUID tenantId, String institutionCode);

    boolean existsByIdTenantIdAndInstitutionName(UUID tenantId, String institutionName);

    long countByIdTenantIdAndStatusNot(UUID tenantId, String status);
}
