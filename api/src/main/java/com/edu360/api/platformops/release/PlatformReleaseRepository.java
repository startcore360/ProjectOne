package com.edu360.api.platformops.release;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformReleaseRepository extends JpaRepository<PlatformRelease, UUID> {
    Optional<PlatformRelease> findByVersionLabel(String versionLabel);
    List<PlatformRelease> findAllByOrderByCreatedAtDesc();
}
