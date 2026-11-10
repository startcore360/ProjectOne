package com.edu360.api.institution;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps app.institution: college or school inside a tenant.
 * Subject to tenant RLS and activation gate C7.
 */
@Entity
@Table(name = "institution", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Institution {

    @EmbeddedId
    private InstitutionId id;

    @Column(name = "institution_type", nullable = false)
    private String institutionType;

    @Column(name = "institution_code", nullable = false)
    private String institutionCode;

    @Column(name = "institution_name", nullable = false)
    private String institutionName;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "external_university_mode", nullable = false)
    @Builder.Default
    private boolean externalUniversityMode = false;

    @Column(name = "under_university", nullable = false)
    @Builder.Default
    private boolean underUniversity = false;

    @Column(name = "address_line")
    private String addressLine;

    @Column(name = "city")
    private String city;

    @Column(name = "state_name")
    private String stateName;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "principal_invite_email")
    private String principalInviteEmail;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private Integer rowVersion = 1;

    public UUID getTenantId() {
        return id != null ? id.getTenantId() : null;
    }

    public UUID getInstitutionId() {
        return id != null ? id.getInstitutionId() : null;
    }
}
