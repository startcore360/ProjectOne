package com.edu360.api.platformops.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.edu360.api.platformops.billing.Invoice;

public record InvoiceResponse(
        UUID invoiceId,
        UUID tenantId,
        String invoiceNumber,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        UUID discountAppliedBy,
        BigDecimal totalAmount,
        String currencyCode,
        String status,
        Instant sentAt,
        Instant paidAt,
        Instant overdueFlaggedAt,
        Integer rowVersion
) {
    public static InvoiceResponse fromEntity(Invoice i) {
        return new InvoiceResponse(
                i.getInvoiceId(),
                i.getTenantId(),
                i.getInvoiceNumber(),
                i.getPeriodStart(),
                i.getPeriodEnd(),
                i.getSubtotalAmount(),
                i.getDiscountAmount(),
                i.getDiscountAppliedBy(),
                i.getTotalAmount(),
                i.getCurrencyCode(),
                i.getStatus(),
                i.getSentAt(),
                i.getPaidAt(),
                i.getOverdueFlaggedAt(),
                i.getRowVersion()
        );
    }
}
