package com.edu360.api.platformops.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EscalateSupportTicketRequest(
        @NotBlank @Pattern(regexp = "ENGINEERING|RELEASE_MANAGER") String to,
        @NotBlank @Size(max = 5000) String note
) {
}
