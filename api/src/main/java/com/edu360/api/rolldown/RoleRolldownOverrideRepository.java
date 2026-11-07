package com.edu360.api.rolldown;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRolldownOverrideRepository extends JpaRepository<RoleRolldownOverride, RoleRolldownOverrideId> {
    List<RoleRolldownOverride> findByIdTenantIdAndStatus(UUID tenantId, String status);
    List<RoleRolldownOverride> findByIdTenantIdAndInstitutionIdAndStatus(UUID tenantId, UUID institutionId, String status);
    Optional<RoleRolldownOverride> findByIdTenantIdAndIdRoleRolldownOverrideId(UUID tenantId, UUID overrideId);
}
