package com.edu360.api.platformops.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/platform/integration-types. Only the TYPE catalogue fields - e.g.
 * integrationTypeCode="SMS_DLT", never a tenant's provider key/secret/webhook (those live in
 * app.channel_config.secret_ref, a different, tenant-scoped table this endpoint never touches).
 */
public record CreateIntegrationTypeRequest(
        @NotBlank @Size(max = 100) String integrationTypeCode,
        @NotBlank @Size(min = 1, max = 300) String description,
        List<String> allowedProviders) {
}
