package com.edu360.api.platformops.billing.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ApplyDiscountRequest(
        @NotNull @Positive BigDecimal discountAmount,
        String reason
) {
    public ApplyDiscountRequest(BigDecimal discountAmount) {
        this(discountAmount, null);
    }
}
