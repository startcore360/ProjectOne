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
 * Mapping of app.program_year - a year within a degree program.
 */
@Entity
@Table(schema = "app", name = "program_year")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProgramYear {

    @Id
    @Column(name = "program_year_id")
    private UUID programYearId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "year_number", nullable = false)
    private Short yearNumber;

    @Column(name = "label", nullable = false)
    private String label;

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
