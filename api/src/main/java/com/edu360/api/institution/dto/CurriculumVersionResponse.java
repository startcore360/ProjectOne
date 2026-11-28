package com.edu360.api.institution.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record CurriculumVersionResponse(
        UUID programCurriculumVersionId,
        UUID institutionId,
        UUID programId,
        Integer versionNumber,
        JsonNode curriculumJson,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Instant createdAt,
        UUID createdBy
) {}
