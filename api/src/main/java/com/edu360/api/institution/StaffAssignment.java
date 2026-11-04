package com.edu360.api.institution;

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
 * WHAT: a read-only mapping of app.staff_assignment - the ONE table in the committed schema that
 * actually represents "this person is faculty/staff of this department" (department_id, nullable
 * = university-level staff with no department). HOD_ASSIGN and MENTOR_ASSIGN both check
 * departmentId here rather than inventing a separate membership table, because this is the table
 * the schema itself designates for it (see app.department.hod_staff_assignment_id's comment and
 * app.mentor_assignment.staff_assignment_id's comment).
 */
@Entity
@Table(schema = "app", name = "staff_assignment")
@Getter
@Setter
@NoArgsConstructor
public class StaffAssignment {

    @Id
    @Column(name = "staff_assignment_id")
    private UUID staffAssignmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "staff_profile_id", nullable = false)
    private UUID staffProfileId;

    /** NULL = university-level staff. */
    @Column(name = "institution_id")
    private UUID institutionId;

    /** College only; non-null means this person is faculty of that department. */
    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "designation", nullable = false)
    private String designation;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "status", nullable = false)
    private String status;
}
