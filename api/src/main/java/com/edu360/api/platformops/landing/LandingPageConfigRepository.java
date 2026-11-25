package com.edu360.api.platformops.landing;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingPageConfigRepository extends JpaRepository<LandingPageConfig, UUID> {
}
