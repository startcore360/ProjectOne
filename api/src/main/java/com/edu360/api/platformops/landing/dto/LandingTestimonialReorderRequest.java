package com.edu360.api.platformops.landing.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

public record LandingTestimonialReorderRequest(
        @NotEmpty List<UUID> orderedIds
) { }
