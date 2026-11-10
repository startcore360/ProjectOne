package com.edu360.api.imports;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity representing an invalid row from an import job in app.import_row_error.
 * Kept to fix and add later [Rule MD-27].
 */
@Entity
@Table(schema = "app", name = "import_row_error")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportRowError {

    @EmbeddedId
    private ImportRowErrorId id;

    @Column(name = "import_job_id", nullable = false)
    private UUID importJobId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "error_code", nullable = false)
    private String errorCode;

    @Column(name = "error_message", nullable = false)
    private String errorMessage;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "row_data", columnDefinition = "jsonb")
    private String rowData;

    @Column(name = "fixed_at")
    private Instant fixedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version")
    private Integer rowVersion;
}
