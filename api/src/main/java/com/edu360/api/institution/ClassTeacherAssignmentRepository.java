package com.edu360.api.institution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassTeacherAssignmentRepository extends JpaRepository<ClassTeacherAssignment, UUID> {

    Optional<ClassTeacherAssignment> findByTenantIdAndSectionIdAndEffectiveToIsNull(UUID tenantId, UUID sectionId);
}
