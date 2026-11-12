package com.edu360.api.institution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, StaffProfileId> {
    Optional<StaffProfile> findByIdTenantIdAndIdStaffProfileId(UUID tenantId, UUID staffProfileId);
    Optional<StaffProfile> findByIdTenantIdAndUserAccountId(UUID tenantId, UUID userAccountId);
    Optional<StaffProfile> findByIdTenantIdAndStaffCode(UUID tenantId, String staffCode);
    boolean existsByIdTenantIdAndStaffCode(UUID tenantId, String staffCode);

    @Query(value = """
            SELECT DISTINCT p.*
              FROM app.staff_profile p
             WHERE p.tenant_id = :tenantId
               AND (:query IS NULL OR p.full_name ILIKE '%' || :query || '%'
                    OR p.staff_code ILIKE '%' || :query || '%'
                    OR COALESCE(p.personal_email, '') ILIKE '%' || :query || '%')
               AND (:institutionId IS NULL OR EXISTS (
                    SELECT 1 FROM app.staff_assignment a
                     WHERE a.tenant_id=p.tenant_id AND a.staff_profile_id=p.staff_profile_id
                       AND a.status='ACTIVE' AND a.institution_id=:institutionId
                       AND (:departmentId IS NULL OR a.department_id=:departmentId)))
             ORDER BY p.full_name, p.staff_profile_id
             LIMIT :limit
            """, nativeQuery = true)
    List<StaffProfile> searchTenantStaff(@Param("tenantId") UUID tenantId,
            @Param("institutionId") UUID institutionId, @Param("departmentId") UUID departmentId,
            @Param("query") String query, @Param("limit") int limit);
}
