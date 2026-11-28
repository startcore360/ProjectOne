package com.edu360.api.platformops.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.edu360.api.platformops.billing.InvoiceLine;

public record InvoiceLineResponse(
        UUID invoiceLineId,
        UUID invoiceId,
        String lineType,
        String description,
        BigDecimal quantity,
        BigDecimal unitAmount,
        BigDecimal lineAmount,
        UUID tenantUsageMonthlyId,
        Instant createdAt
) {
    public static InvoiceLineResponse fromEntity(InvoiceLine line) {
        return new InvoiceLineResponse(line.getInvoiceLineId(), line.getInvoiceId(), line.getLineType(),
                line.getDescription(), line.getQuantity(), line.getUnitAmount(), line.getLineAmount(),
                line.getTenantUsageMonthlyId(), line.getCreatedAt());
    }
}
