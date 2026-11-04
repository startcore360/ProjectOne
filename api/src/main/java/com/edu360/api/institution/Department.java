package com.edu360.api.institution;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: a mapping of app.department (sql/01_schema.sql) - a college department. Schools have
 * none in v1 (HI-12), so every row here belongs to a COLLEGE institution.
 *
 * WHY hodStaffAssignmentId is writable here and nowhere else yet: this is exactly the field
 * HOD_ASSIGN ("Make a faculty member HOD") changes. The schema comment on that column says the
 * "must be faculty of this department" rule is enforced by "service + trigger" - there is no
 * such trigger committed in sql/02_rules.sql or sql/03_bindings.sql today (checked; only
 * trg_touch and the tenant/institution RLS policies exist for this table), so
 * DepartmentSetupService carries that whole rule itself. This is a real gap between the schema
 * comment and the committed SQL, flagged rather than silently relied upon.
 */
@Entity
@Table(schema = "app", name = "department")
@Getter
@Setter
@NoArgsConstructor
public class Department {

    @Id
    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "department_code", nullable = false)
    private String departmentCode;

    @Column(name = "department_name", nullable = false)
    private String departmentName;

    /** HOD_ASSIGN target: an app.staff_assignment row that must belong to this department. */
    @Column(name = "hod_staff_assignment_id")
    private UUID hodStaffAssignmentId;

    @Column(name = "status", nullable = false)
    private String status;

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
