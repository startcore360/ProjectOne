package com.edu360.api.platformops.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {
    List<InvoiceLine> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);
    Optional<InvoiceLine> findByInvoiceLineIdAndInvoiceId(UUID invoiceLineId, UUID invoiceId);
}
