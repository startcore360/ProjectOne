package com.edu360.api.platformops;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationTypeRepository extends JpaRepository<IntegrationType, UUID> {

    List<IntegrationType> findAllByOrderByIntegrationTypeCode();

    /** Used to enforce uq_integration_type_integration_type_code at the application layer before
     *  insert, so a duplicate code comes back as a clean 400 rather than a raw constraint-
     *  violation 500. */
    Optional<IntegrationType> findByIntegrationTypeCode(String integrationTypeCode);
}
