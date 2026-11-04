package com.edu360.api.institution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MentorAssignmentRepository extends JpaRepository<MentorAssignment, UUID> {

    Optional<MentorAssignment> findByTenantIdAndSectionIdAndEffectiveToIsNull(UUID tenantId, UUID sectionId);
}
