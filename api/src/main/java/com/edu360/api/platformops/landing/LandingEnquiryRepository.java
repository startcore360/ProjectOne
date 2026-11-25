package com.edu360.api.platformops.landing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingEnquiryRepository extends JpaRepository<LandingEnquiry, UUID> {
    List<LandingEnquiry> findAllByOrderByCreatedAtDesc();

    List<LandingEnquiry> findByEnquiryTypeOrderByCreatedAtDesc(String enquiryType);

    List<LandingEnquiry> findByStatusOrderByCreatedAtDesc(String status);

    List<LandingEnquiry> findByEnquiryTypeAndStatusOrderByCreatedAtDesc(String enquiryType, String status);
}
