package com.edu360.api.platformops.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edu360.api.platformops.IntegrationType;

public record IntegrationTypeResponse(
        UUID integrationTypeId,
        String integrationTypeCode,
        String description,
        String status,
        List<String> allowedProviders,
        Instant createdAt,
        Instant updatedAt,
        Integer rowVersion) {

    public static IntegrationTypeResponse from(IntegrationType entity) {
        return new IntegrationTypeResponse(
                entity.getIntegrationTypeId(),
                entity.getIntegrationTypeCode(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getAllowedProviders(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRowVersion());
    }
}
