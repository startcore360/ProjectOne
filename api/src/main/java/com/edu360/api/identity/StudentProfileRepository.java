package com.edu360.api.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    Optional<StudentProfile> findByTenantIdAndUserAccountId(UUID tenantId, UUID userAccountId);

    boolean existsByTenantIdAndStudentCode(UUID tenantId, String studentCode);

    /** Parent self-service: only ACTIVE links owned by this guardian account grant child access. */
    @Query(value = """
            SELECT DISTINCT s.*
              FROM app.student_profile s
              JOIN app.student_guardian_link l
                ON l.tenant_id = s.tenant_id AND l.student_profile_id = s.student_profile_id
              JOIN app.guardian_profile g
                ON g.tenant_id = l.tenant_id AND g.guardian_profile_id = l.guardian_profile_id
             WHERE s.tenant_id = :tenantId
               AND g.user_account_id = :guardianUserId
               AND l.status = 'ACTIVE'
             ORDER BY s.student_code
            """, nativeQuery = true)
    List<StudentProfile> findActiveChildrenByGuardianUser(
            @Param("tenantId") UUID tenantId,
            @Param("guardianUserId") UUID guardianUserId);
}
