package com.edu360.api.platformops.role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformStepInRepository extends JpaRepository<PlatformStepIn, UUID> {

    boolean existsByActorUserIdAndActionCodeAndExpiresAtAfter(UUID actorUserId, String actionCode, Instant now);

    List<PlatformStepIn> findTop50ByOrderByStartedAtDesc();

    List<PlatformStepIn> findByActorUserIdAndExpiresAtAfter(UUID actorUserId, Instant now);
}
