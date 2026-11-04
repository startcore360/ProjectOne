package com.edu360.api.institution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    Optional<Section> findByTenantIdAndSectionId(UUID tenantId, UUID sectionId);
}
