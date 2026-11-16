package com.edu360.api.platformops;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformNotificationRepository extends JpaRepository<PlatformNotification, UUID> {
}
