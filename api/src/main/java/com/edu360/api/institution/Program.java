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
 * WHAT: mapping of app.program (sql/01_schema.sql) - a degree program (e.g. B.Tech, M.Sc).
 */
@Entity
@Table(schema = "app", name = "program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {

    @Id
    @Column(name = "program_id")
    private UUID programId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "program_code", nullable = false)
    private String programCode;

    @Column(name = "program_name", nullable = false)
    private String programName;

    @Column(name = "duration_years", nullable = false)
    private Short durationYears;

    @Column(name = "semesters_per_year", nullable = false)
    private Short semestersPerYear;

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
