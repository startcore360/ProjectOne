package com.edu360.api.platformops.billing.dto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
public record UpdateBillingSettingsRequest(@NotNull @Min(1) @Max(365) Integer invoiceDueDays) {}
