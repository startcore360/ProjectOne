package com.edu360.api.platformops.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformSettingValueRepository extends JpaRepository<PlatformSettingValue, UUID> {
    Optional<PlatformSettingValue> findBySettingKeyAndStatus(String settingKey, String status);
}
