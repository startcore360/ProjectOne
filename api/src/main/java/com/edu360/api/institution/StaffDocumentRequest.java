package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StaffDocumentRequest(
        @NotNull String documentType,
        @NotNull UUID fileId,
        @Size(max = 150) String qualificationTitle,
        @Size(max = 150) String awardingInstitution,
        String qualificationLevel,
        LocalDate awardedOn
) {}
