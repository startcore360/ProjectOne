package com.edu360.api.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GradingSchemeVersionRepository extends JpaRepository<GradingSchemeVersion, GradingSchemeVersionId> {
    List<GradingSchemeVersion> findByIdTenantId(UUID tenantId);
    Optional<GradingSchemeVersion> findByIdTenantIdAndIdGradingSchemeVersionId(UUID tenantId, UUID versionId);
}
