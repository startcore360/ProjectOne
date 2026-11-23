package com.edu360.api.institution.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

public record CreateCurriculumVersionRequest(
        @NotNull(message = "curriculumJson is required") JsonNode curriculumJson,
        @NotNull(message = "effectiveFrom is required") LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
