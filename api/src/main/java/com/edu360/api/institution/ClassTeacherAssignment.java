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
 * WHAT: a mapping of app.class_teacher_assignment - "Class Teacher of a school section
 * (CLASS_TEACHER_ASSIGN)". The table comment flags its own open decision: "Unit: section
 * (OD-24 asks class vs section)" - OD-24 is still open per the governance register, so this
 * entity/service deliberately follow what the committed table actually has a FK for (section_id,
 * not class_id) rather than guessing the open decision's outcome. See
 * ClassTeacherAssignmentService's javadoc.
 */
@Entity
@Table(schema = "app", name = "class_teacher_assignment")
@Getter
@Setter
@NoArgsConstructor
public class ClassTeacherAssignment {

    @Id
    @Column(name = "class_teacher_assignment_id")
    private UUID classTeacherAssignmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

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
