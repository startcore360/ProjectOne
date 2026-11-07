package com.edu360.api.termlock.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record LockTermRequest(
        @NotNull UUID academicSessionId,
        UUID semesterId,
        String notes
) {
}
