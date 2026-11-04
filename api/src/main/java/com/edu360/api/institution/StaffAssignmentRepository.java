package com.edu360.api.institution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, UUID> {

    Optional<StaffAssignment> findByTenantIdAndStaffAssignmentId(UUID tenantId, UUID staffAssignmentId);
}
