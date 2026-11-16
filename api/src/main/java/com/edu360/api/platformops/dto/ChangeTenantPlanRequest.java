package com.edu360.api.platformops.dto;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
public record ChangeTenantPlanRequest(@NotNull UUID planId,@NotNull PlanChangeEffective effective) {}
