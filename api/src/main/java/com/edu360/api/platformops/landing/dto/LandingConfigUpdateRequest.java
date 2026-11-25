package com.edu360.api.platformops.landing.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LandingConfigUpdateRequest(
        @NotBlank @Size(max = 160) String heroEyebrow,
        @NotBlank @Size(max = 200) String heroTitle,
        @NotBlank @Size(max = 800) String heroSubtitle,
        @NotBlank @Size(max = 200) String heroNote,
        @NotBlank @Size(max = 200) String trustLabel,
        @NotBlank String whyChooseJson,
        @NotBlank String roleBenefitsJson,
        @NotBlank String solutionsJson,
        @NotBlank String stepsJson,
        @NotBlank @Email @Size(max = 180) String contactEmail,
        @NotBlank @Size(max = 240) String footerTagline,
        UUID heroLightMediaId,
        UUID heroDarkMediaId,
        UUID showcaseLightMediaId,
        UUID showcaseDarkMediaId
) { }
