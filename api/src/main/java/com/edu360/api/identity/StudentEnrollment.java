package com.edu360.api.identity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A student's dated placement in one institution, academic session and section. */
@Entity
@Table(schema = "app", name = "student_enrollment")
@Getter
@Setter
@NoArgsConstructor
public class StudentEnrollment {
    @EmbeddedId
    private StudentEnrollmentId id;

    @Column(name = "student_profile_id", nullable = false)
    private UUID studentProfileId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "academic_session_id", nullable = false)
    private UUID academicSessionId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "roll_number")
    private String rollNumber;

    @Column(name = "outcome", nullable = false)
    private String outcome = "IN_PROGRESS";

    @Column(name = "promoted_from_enrollment_id")
    private UUID promotedFromEnrollmentId;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

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

    public UUID getTenantId() {
        return id == null ? null : id.getTenantId();
    }

    public UUID getEnrollmentId() {
        return id == null ? null : id.getEnrollmentId();
    }
}
