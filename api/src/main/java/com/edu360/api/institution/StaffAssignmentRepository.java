package com.edu360.api.institution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, UUID> {

    Optional<StaffAssignment> findByTenantIdAndStaffAssignmentId(UUID tenantId, UUID staffAssignmentId);
    Optional<StaffAssignment> findByTenantIdAndStaffAssignmentIdAndStaffProfileId(
            UUID tenantId, UUID staffAssignmentId, UUID staffProfileId);
    java.util.List<StaffAssignment> findByTenantIdAndStaffProfileIdOrderByEffectiveFromDesc(
            UUID tenantId, UUID staffProfileId);
    boolean existsByTenantIdAndStaffProfileIdAndInstitutionIdAndDepartmentIdAndStatus(
            UUID tenantId, UUID staffProfileId, UUID institutionId, UUID departmentId, String status);
}
