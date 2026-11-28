package com.edu360.api.platformops.subscription.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CustomerQuoteRequest(
        @NotBlank String tenantType,
        @Min(0) int colleges,
        @Min(0) int schools,
        @Min(0) int students,
        @Min(0) int staff,
        List<String> requiredModules,
        @Min(1) @Max(36) int billingCycleMonths,
        boolean includeSetupFee
) { }
