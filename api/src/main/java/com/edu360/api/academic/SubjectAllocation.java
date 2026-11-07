package com.edu360.api.academic;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subject_allocation", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectAllocation {

    @EmbeddedId
    private SubjectAllocationId id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "course_offering_id", nullable = false)
    private UUID courseOfferingId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "subject_group_id")
    private UUID subjectGroupId;

    @Column(name = "staff_assignment_id", nullable = false)
    private UUID staffAssignmentId;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private int rowVersion = 1;
}
