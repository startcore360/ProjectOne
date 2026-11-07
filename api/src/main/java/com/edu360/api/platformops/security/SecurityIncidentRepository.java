package com.edu360.api.platformops.security;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, UUID> {
    List<SecurityIncident> findAllByOrderByDetectedAtDesc();
    List<SecurityIncident> findByTenantIdOrderByDetectedAtDesc(UUID tenantId);
}
