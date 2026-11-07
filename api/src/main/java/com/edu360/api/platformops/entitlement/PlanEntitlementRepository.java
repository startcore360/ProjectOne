package com.edu360.api.platformops.entitlement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, UUID> {
    List<PlanEntitlement> findByPlanId(UUID planId);
    Optional<PlanEntitlement> findByPlanIdAndModuleCodeAndFeatureCode(UUID planId, String moduleCode, String featureCode);
}
