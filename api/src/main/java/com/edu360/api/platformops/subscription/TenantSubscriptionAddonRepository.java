package com.edu360.api.platformops.subscription;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantSubscriptionAddonRepository extends JpaRepository<TenantSubscriptionAddon, UUID> {
    List<TenantSubscriptionAddon> findByStatusOrderByCreatedAtAsc(String status);
    List<TenantSubscriptionAddon> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<TenantSubscriptionAddon> findByInvoiceId(UUID invoiceId);
    List<TenantSubscriptionAddon> findAllByOrderByCreatedAtDesc();

    @Query(value = """
            SELECT COALESCE(SUM(a.quantity * ci.units_per_quantity), 0)
              FROM platform.tenant_subscription_addon a
              JOIN platform.commercial_catalog_item ci ON ci.catalog_item_id = a.catalog_item_id
             WHERE a.tenant_id = :tenantId
               AND a.status = 'ACTIVE'
               AND a.effective_from <= :today
               AND (a.effective_to IS NULL OR a.effective_to >= :today)
               AND ci.category = 'CAPACITY'
               AND ci.unit_code = :unitCode
            """, nativeQuery = true)
    BigDecimal activeCapacity(@Param("tenantId") UUID tenantId, @Param("unitCode") String unitCode,
            @Param("today") LocalDate today);
}
