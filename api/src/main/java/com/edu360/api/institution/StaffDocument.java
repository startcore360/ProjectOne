package com.edu360.api.institution;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "app", name = "staff_document")
@Getter
@Setter
@NoArgsConstructor
public class StaffDocument {
    @EmbeddedId
    private StaffDocumentId id;
    @Column(name = "staff_profile_id", nullable = false)
    private UUID staffProfileId;
    @Column(name = "document_type", nullable = false)
    private String documentType;
    @Column(name = "file_id", nullable = false)
    private UUID fileId;
    @Column(name = "verification_status", nullable = false)
    private String verificationStatus = "UPLOADED";
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "verified_at")
    private Instant verifiedAt;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}
