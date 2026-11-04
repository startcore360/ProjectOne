package com.edu360.api.institution;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: a minimal, read-only mapping of app.semester - only the FK MentorAssignmentService needs
 * to climb semester -> program_year -> program -> department_id and find which department owns a
 * college section (MENTOR_ASSIGN's "faculty of the section department" rule). Not the full
 * semester record (semesterNumber, label, etc. belong to a later academic-structure story).
 */
@Entity
@Table(schema = "app", name = "semester")
@Getter
@Setter
@NoArgsConstructor
public class Semester {

    @Id
    @Column(name = "semester_id")
    private UUID semesterId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "program_year_id", nullable = false)
    private UUID programYearId;
}
