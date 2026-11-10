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
 * Entity representing an import batch job in app.import_job.
 * Enforces Rule MD-27: Whole file checked first; clean file or good rows only.
 */
@Entity
@Table(schema = "app", name = "import_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportJob {

    @EmbeddedId
    private ImportJobId id;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "import_kind", nullable = false)
    private String importKind;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "source_file_id")
    private UUID sourceFileId;

    @Column(nullable = false)
    private String status;

    @Column(name = "import_mode")
    private String importMode;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "valid_rows")
    private Integer validRows;

    @Column(name = "error_rows")
    private Integer errorRows;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

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
