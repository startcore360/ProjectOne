package com.edu360.api.selfservice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UserDeviceId> {

    List<UserDevice> findByIdTenantIdAndUserAccountIdAndRevokedAtIsNull(UUID tenantId, UUID userAccountId);

    Optional<UserDevice> findByIdTenantIdAndUserAccountIdAndBrowserKeyHashAndRevokedAtIsNull(
            UUID tenantId, UUID userAccountId, String browserKeyHash);

    Optional<UserDevice> findByIdTenantIdAndKeycloakSessionId(UUID tenantId, String keycloakSessionId);
}
