package com.edu360.api.institution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, StaffProfileId> {
    Optional<StaffProfile> findByIdTenantIdAndIdStaffProfileId(UUID tenantId, UUID staffProfileId);
    Optional<StaffProfile> findByIdTenantIdAndUserAccountId(UUID tenantId, UUID userAccountId);
    Optional<StaffProfile> findByIdTenantIdAndStaffCode(UUID tenantId, String staffCode);
    boolean existsByIdTenantIdAndStaffCode(UUID tenantId, String staffCode);
}
