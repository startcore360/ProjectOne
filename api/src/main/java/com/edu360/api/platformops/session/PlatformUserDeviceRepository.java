package com.edu360.api.platformops.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformUserDeviceRepository extends JpaRepository<PlatformUserDevice, UUID> {

    List<PlatformUserDevice> findByPlatformUserIdAndRevokedAtIsNull(UUID platformUserId);

    Optional<PlatformUserDevice> findByPlatformUserIdAndBrowserKeyHashAndRevokedAtIsNull(
            UUID platformUserId, String browserKeyHash);

    Optional<PlatformUserDevice> findByKeycloakSessionId(String keycloakSessionId);
}
