package com.edu360.api.platformops.subscription.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record AddonDecisionRequest(
        @DecimalMin("0.00") BigDecimal unitAmount,
        @Size(max = 1000) String reason
) { }
