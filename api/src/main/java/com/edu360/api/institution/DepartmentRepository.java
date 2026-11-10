package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByTenantIdAndDepartmentId(UUID tenantId, UUID departmentId);

    List<Department> findByTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);

    boolean existsByTenantIdAndInstitutionIdAndDepartmentCode(UUID tenantId, UUID institutionId, String departmentCode);
}
