package com.edu360.api.guardian;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentGuardianLinkRepository extends JpaRepository<StudentGuardianLink, StudentGuardianLinkId> {

    List<StudentGuardianLink> findByIdTenantIdAndStudentProfileId(UUID tenantId, UUID studentProfileId);

    Optional<StudentGuardianLink> findByIdTenantIdAndIdStudentGuardianLinkId(UUID tenantId, UUID studentGuardianLinkId);

    long countByIdTenantIdAndStudentProfileId(UUID tenantId, UUID studentProfileId);
}
