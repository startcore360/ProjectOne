package com.edu360.api.platformops.landing.dto;

import java.util.List;
import java.util.UUID;

public record PlatformLandingCmsResponse(
        ConfigDto config,
        List<LandingTestimonialResponse> testimonials,
        List<DirectoryTenantItem> directoryTenants
) {
    public record ConfigDto(
            UUID configId,
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
            Integer rowVersion
    ) { }

    public record DirectoryTenantItem(
            UUID tenantId,
            String tenantName,
            String subdomain,
            String tenantType,
            String state,
            boolean listedInDirectory
    ) { }
}
