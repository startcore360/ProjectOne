package com.edu360.api.institution;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record StaffDocumentRequest(
        @NotNull String documentType,
        @NotNull UUID fileId
) {}
