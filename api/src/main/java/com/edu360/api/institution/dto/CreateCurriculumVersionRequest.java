package com.edu360.api.institution.dto;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record CreateCurriculumVersionRequest(
        @JsonProperty("curriculumJson") @NotNull(message = "curriculumJson is required") Map<String, Object> curriculumJson,
        @JsonProperty("effectiveFrom") @NotNull(message = "effectiveFrom is required") LocalDate effectiveFrom,
        @JsonProperty("effectiveTo") LocalDate effectiveTo
) {
}
