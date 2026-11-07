package com.edu360.api.institution;

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

/**
 * Mapping of app.course_offering - a subject offered in a session for a semester or class.
 */
@Entity
@Table(schema = "app", name = "course_offering")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CourseOffering {

    @Id
    @Column(name = "course_offering_id")
    private UUID courseOfferingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "academic_session_id", nullable = false)
    private UUID academicSessionId;

    /** College only. */
    @Column(name = "semester_id")
    private UUID semesterId;

    /** School only. */
    @Column(name = "class_id")
    private UUID classId;

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
