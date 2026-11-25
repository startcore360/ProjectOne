package com.edu360.api.platformops.landing.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LandingTestimonialRequest(
        @NotBlank @Pattern(regexp = "TEXT|VIDEO") String kind,
        @NotBlank @Size(max = 120) String authorName,
        @NotBlank @Size(max = 120) String authorRole,
        @NotBlank @Size(max = 180) String institutionName,
        @NotBlank @Size(max = 1000) String quoteText,
        @Size(max = 500) String videoUrl,
        UUID videoMediaId,
        UUID posterMediaId,
        @Min(0) int displayOrder,
        boolean published
) { }
