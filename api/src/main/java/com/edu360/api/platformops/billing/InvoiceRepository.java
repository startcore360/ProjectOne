package com.edu360.api.platformops.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findAllByOrderByCreatedAtDesc();
    List<Invoice> findByStatus(String status);
    List<Invoice> findByStatusIn(List<String> statuses);
    boolean existsByTenantIdAndStatus(UUID tenantId, String status);
    boolean existsByTenantIdAndPeriodStartAndPeriodEnd(UUID tenantId, java.time.LocalDate periodStart, java.time.LocalDate periodEnd);
    List<Invoice> findByStatusAndDueAtBeforeOrderByDueAtAsc(String status, java.time.LocalDate dueBefore);
    boolean existsByTenantIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(UUID tenantId, java.time.LocalDate start, java.time.LocalDate end);
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                  FROM platform.invoice i
                  JOIN platform.invoice_line l ON l.invoice_id = i.invoice_id
                 WHERE i.tenant_id = :tenantId
                   AND i.status IN ('APPROVED', 'SENT', 'PAID')
                   AND l.line_type = 'SETUP'
            )
            """, nativeQuery = true)
    boolean existsChargedSetupFee(@Param("tenantId") UUID tenantId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                  FROM platform.invoice i
                  JOIN platform.invoice_line l ON l.invoice_id = i.invoice_id
                 WHERE i.tenant_id = :tenantId
                   AND i.status IN ('APPROVED', 'SENT', 'PAID')
                   AND l.catalog_item_id = :catalogItemId
            )
            """, nativeQuery = true)
    boolean existsChargedCatalogItem(@Param("tenantId") UUID tenantId,
            @Param("catalogItemId") UUID catalogItemId);
}
