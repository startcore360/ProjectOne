package com.edu360.api.platformops.support.dto;

import java.util.UUID;

public record UpdateSupportTicketRequest(
        String status,
        UUID assignedTo,
        String resolutionNote,
        String priority,
        String category
) {
    public UpdateSupportTicketRequest(String status, UUID assignedTo, String resolutionNote) {
        this(status, assignedTo, resolutionNote, null, null);
    }
}
