package com.edu360.api.platformops.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Size;

public record UpdatePlanRequest(
        @Size(min = 2, max = 100, message = "Plan name must be between 2 and 100 characters")
        String planName,

        BigDecimal priceAmount,

        String currencyCode,

        Integer userLimit,

        Integer storageLimitGb,

        Integer smsLimit,

        Integer apiLimit,

        Integer maxInstitutions,

        String status
) {}
