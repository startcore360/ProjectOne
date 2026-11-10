package com.edu360.api.institution;

import java.time.Instant;
import java.time.LocalDate;
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
 * Mapping of app.academic_session (sql/01_schema.sql) - Academic year; master set by
 * the university, own session for a standalone institution [7.12, PQ-11].
 */
@Entity
@Table(schema = "app", name = "academic_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicSession {

    @Id
    @Column(name = "academic_session_id")
    private UUID academicSessionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** NULL = tenant-wide master session; set = institution session. */
    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "session_label", nullable = false)
    private String sessionLabel;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

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
