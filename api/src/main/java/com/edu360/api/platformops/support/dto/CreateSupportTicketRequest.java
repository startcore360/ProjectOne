package com.edu360.api.platformops.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 10000) String description,
        String priority,
        String category
) {}
