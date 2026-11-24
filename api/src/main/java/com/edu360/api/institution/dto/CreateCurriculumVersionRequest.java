package com.edu360.api.institution.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.validation.constraints.NotNull;

public record CreateCurriculumVersionRequest(
        @NotNull(message = "curriculumJson is required") JsonNode curriculumJson,
        @NotNull(message = "effectiveFrom is required") LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
    @JsonCreator
    public CreateCurriculumVersionRequest(
            @JsonProperty("curriculumJson") ObjectNode curriculumJson,
            @JsonProperty("effectiveFrom") LocalDate effectiveFrom,
            @JsonProperty("effectiveTo") LocalDate effectiveTo) {
        this((JsonNode) curriculumJson, effectiveFrom, effectiveTo);
    }
}
