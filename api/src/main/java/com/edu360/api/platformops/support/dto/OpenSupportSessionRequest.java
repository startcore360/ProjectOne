package com.edu360.api.platformops.support.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record OpenSupportSessionRequest(@NotNull UUID ticketId) {
}
