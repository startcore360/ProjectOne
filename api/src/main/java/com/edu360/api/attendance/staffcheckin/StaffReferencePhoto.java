package com.edu360.api.attendance.staffcheckin;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps app.staff_reference_photo: a consented enrolment photo a human reviewer can look at next to
 * a provisional check-in selfie. It is never fed into automated face matching - the feature does
 * no identity verification; liveness only scores whether the capture looks like a live camera
 * sequence. Not yet used by any endpoint (enrolment upload is a later sub-task).
 */
@Entity
@Table(schema = "app", name = "staff_reference_photo")
@IdClass(StaffReferencePhoto.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffReferencePhoto {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Id
    @Column(name = "staff_reference_photo_id")
    private UUID staffReferencePhotoId;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "photo_file_id", nullable = false)
    private UUID photoFileId;

    @Column(name = "uploaded_at", insertable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "consent_given_at", nullable = false)
    private Instant consentGivenAt;

    @Column(name = "consent_text_version", nullable = false)
    private String consentTextVersion;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements java.io.Serializable {
        private UUID tenantId;
        private UUID staffReferencePhotoId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk other)) return false;
            return java.util.Objects.equals(tenantId, other.tenantId)
                    && java.util.Objects.equals(staffReferencePhotoId, other.staffReferencePhotoId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tenantId, staffReferencePhotoId);
        }
    }
}
