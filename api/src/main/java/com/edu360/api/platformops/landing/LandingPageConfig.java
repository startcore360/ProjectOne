package com.edu360.api.platformops.landing;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(schema = "platform", name = "landing_page_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LandingPageConfig {
    public static final UUID DEFAULT_CONFIG_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Id
    @Column(name = "config_id")
    private UUID configId;

    @Column(name = "hero_eyebrow", nullable = false)
    private String heroEyebrow;

    @Column(name = "hero_title", nullable = false)
    private String heroTitle;

    @Column(name = "hero_subtitle", nullable = false)
    private String heroSubtitle;

    @Column(name = "hero_note", nullable = false)
    private String heroNote;

    @Column(name = "trust_label", nullable = false)
    private String trustLabel;

    @Column(name = "why_choose_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String whyChooseJson;

    @Column(name = "role_benefits_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String roleBenefitsJson;

    @Column(name = "solutions_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String solutionsJson;

    @Column(name = "steps_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String stepsJson;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "footer_tagline", nullable = false)
    private String footerTagline;

    @Column(name = "hero_light_media_id")
    private UUID heroLightMediaId;

    @Column(name = "hero_dark_media_id")
    private UUID heroDarkMediaId;

    @Column(name = "showcase_light_media_id")
    private UUID showcaseLightMediaId;

    @Column(name = "showcase_dark_media_id")
    private UUID showcaseDarkMediaId;

    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private Integer rowVersion = 1;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
