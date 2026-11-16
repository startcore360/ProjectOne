package com.edu360.api.platformops.billing.dto;
import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
public record CreateBillingCycleRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd) {}
