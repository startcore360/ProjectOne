package com.edu360.api.platformops.support.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.platformops.support.SupportTicket;

public record SupportTicketResponse(
        UUID supportTicketId,
        UUID tenantId,
        String ticketNumber,
        UUID raisedByUserId,
        String raisedByName,
        String subject,
        String description,
        String priority,
        String category,
        String status,
        UUID assignedTo,
        Instant escalatedAt,
        Instant closedAt,
        String resolutionNote,
        Instant createdAt,
        Integer rowVersion
) {
    public static SupportTicketResponse fromEntity(SupportTicket t) {
        return new SupportTicketResponse(
                t.getSupportTicketId(),
                t.getTenantId(),
                t.getTicketNumber(),
                t.getRaisedByUserId(),
                t.getRaisedByName(),
                t.getSubject(),
                t.getDescription(),
                t.getPriority(),
                t.getCategory(),
                t.getStatus(),
                t.getAssignedTo(),
                t.getEscalatedAt(),
                t.getClosedAt(),
                t.getResolutionNote(),
                t.getCreatedAt(),
                t.getRowVersion()
        );
    }
}
