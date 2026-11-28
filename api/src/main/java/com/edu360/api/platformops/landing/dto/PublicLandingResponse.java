package com.edu360.api.platformops.landing.dto;

import java.util.List;
import java.util.UUID;

public record PublicLandingResponse(
        String heroEyebrow,
        String heroTitle,
        String heroSubtitle,
        String heroNote,
        String trustLabel,
        String whyChooseJson,
        String roleBenefitsJson,
        String solutionsJson,
        String stepsJson,
        String contactEmail,
        String footerTagline,
        UUID heroLightMediaId,
        UUID heroDarkMediaId,
        UUID showcaseLightMediaId,
        UUID showcaseDarkMediaId,
        List<LandingTestimonialResponse> testimonials,
        List<PublicCatalogModuleItem> catalogModules
) { }
