package com.edu360.api.guardian;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GuardianProfileRepository extends JpaRepository<GuardianProfile, GuardianProfileId> {

    Optional<GuardianProfile> findByIdTenantIdAndMobile(UUID tenantId, String mobile);

    Optional<GuardianProfile> findByIdTenantIdAndIdGuardianProfileId(UUID tenantId, UUID guardianProfileId);
}
