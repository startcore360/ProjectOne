package com.edu360.api.platformops.dto;

import java.util.List;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for PATCH /api/platform/integration-types/{id}. All fields optional (partial
 * update) - a null field leaves that column unchanged. status is constrained to the same two
 * values as ck_integration_type_status.
 */
public record UpdateIntegrationTypeRequest(
        @Size(min = 1, max = 300) String description,
        @Pattern(regexp = "ALLOWED|DISALLOWED") String status,
        List<String> allowedProviders) {
}
