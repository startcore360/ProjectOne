package com.edu360.api.platformops.landing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingTestimonialRepository extends JpaRepository<LandingTestimonial, UUID> {
    List<LandingTestimonial> findByPublishedTrueOrderByDisplayOrderAscCreatedAtDesc();

    List<LandingTestimonial> findAllByOrderByDisplayOrderAscCreatedAtDesc();
}
