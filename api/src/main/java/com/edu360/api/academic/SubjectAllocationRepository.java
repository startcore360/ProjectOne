package com.edu360.api.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectAllocationRepository extends JpaRepository<SubjectAllocation, SubjectAllocationId> {
    List<SubjectAllocation> findByIdTenantId(UUID tenantId);
    List<SubjectAllocation> findByIdTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);
    Optional<SubjectAllocation> findByIdTenantIdAndIdSubjectAllocationId(UUID tenantId, UUID allocationId);
}
