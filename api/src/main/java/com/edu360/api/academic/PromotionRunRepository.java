package com.edu360.api.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionRunRepository extends JpaRepository<PromotionRun, PromotionRunId> {
    List<PromotionRun> findByIdTenantId(UUID tenantId);
    List<PromotionRun> findByIdTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);
    Optional<PromotionRun> findByIdTenantIdAndIdPromotionRunId(UUID tenantId, UUID promotionRunId);
}
