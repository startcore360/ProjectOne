package com.edu360.api.institution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: a mapping of app.mentor_assignment - "Mentor / class coordinator of a college section
 * (MENTOR_ASSIGN by HOD)". A history table (effective_from/effective_to), not a single mutable
 * field: assigning a new mentor ends the current row (sets effectiveTo) and inserts a new one,
 * the same pattern app.staff_assignment itself uses for a person's assignments.
 */
@Entity
@Table(schema = "app", name = "mentor_assignment")
@Getter
@Setter
@NoArgsConstructor
public class MentorAssignment {

    @Id
    @Column(name = "mentor_assignment_id")
    private UUID mentorAssignmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    /** Faculty of the section's department (service-enforced; see Department's javadoc for why). */
    @Column(name = "staff_assignment_id", nullable = false)
    private UUID staffAssignmentId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}
