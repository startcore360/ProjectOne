package com.edu360.api.platformops.landing;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "platform", name = "landing_testimonial")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LandingTestimonial {
    @Id
    @Column(name = "testimonial_id")
    private UUID testimonialId;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_role", nullable = false)
    private String authorRole;

    @Column(name = "institution_name", nullable = false)
    private String institutionName;

    @Column(name = "quote_text", nullable = false)
    private String quoteText;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "video_media_id")
    private UUID videoMediaId;

    @Column(name = "poster_media_id")
    private UUID posterMediaId;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "published", nullable = false)
    @Builder.Default
    private boolean published = true;

    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private Integer rowVersion = 1;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
