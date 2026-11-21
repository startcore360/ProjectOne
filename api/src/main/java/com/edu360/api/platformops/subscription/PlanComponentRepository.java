package com.edu360.api.platformops.subscription;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanComponentRepository extends JpaRepository<PlanComponent, UUID> {
    List<PlanComponent> findByPlanIdAndEnabledTrueOrderByPlanComponentId(UUID planId);
    List<PlanComponent> findByPlanIdOrderByPlanComponentId(UUID planId);
    void deleteByPlanId(UUID planId);

    @Query(value = """
            SELECT COALESCE(SUM(pc.included_quantity * ci.units_per_quantity), 0)
              FROM platform.plan_component pc
              JOIN platform.commercial_catalog_item ci ON ci.catalog_item_id = pc.catalog_item_id
             WHERE pc.plan_id = :planId
               AND pc.enabled = true
               AND ci.category = 'CAPACITY'
               AND ci.unit_code = :unitCode
            """, nativeQuery = true)
    BigDecimal includedCapacity(@Param("planId") UUID planId, @Param("unitCode") String unitCode);
}
