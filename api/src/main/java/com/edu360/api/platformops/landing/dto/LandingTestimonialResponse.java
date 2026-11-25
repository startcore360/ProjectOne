package com.edu360.api.platformops.landing.dto;

import java.util.UUID;

import com.edu360.api.platformops.landing.LandingTestimonial;

public record LandingTestimonialResponse(
        UUID testimonialId,
        String kind,
        String authorName,
        String authorRole,
        String institutionName,
        String quoteText,
        String videoUrl,
        UUID videoMediaId,
        UUID posterMediaId,
        int displayOrder,
        boolean published,
        Integer rowVersion
) {
    public static LandingTestimonialResponse from(LandingTestimonial entity) {
        return new LandingTestimonialResponse(
                entity.getTestimonialId(),
                entity.getKind(),
                entity.getAuthorName(),
                entity.getAuthorRole(),
                entity.getInstitutionName(),
                entity.getQuoteText(),
                entity.getVideoUrl(),
                entity.getVideoMediaId(),
                entity.getPosterMediaId(),
                entity.getDisplayOrder(),
                entity.isPublished(),
                entity.getRowVersion()
        );
    }
}
