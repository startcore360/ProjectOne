package com.edu360.api.platformops.support.dto;

import java.util.UUID;

public record UpdateSupportTicketRequest(
        String status,
        UUID assignedTo,
        String resolutionNote
) {}
