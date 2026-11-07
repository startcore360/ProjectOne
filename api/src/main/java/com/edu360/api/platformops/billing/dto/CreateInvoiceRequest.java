package com.edu360.api.platformops.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateInvoiceRequest(
        @NotNull UUID tenantId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull @PositiveOrZero BigDecimal subtotalAmount,
        String currencyCode
) {}
