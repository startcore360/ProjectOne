package com.edu360.api.platformops.release;

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

/** Schema version and ordered migration-file checksums associated with an application release. */
@Entity
@Table(name = "schema_release", schema = "platform")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSchemaRelease {

    @Id
    @Column(name = "schema_release_id", nullable = false)
    private UUID schemaReleaseId;

    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Column(name = "schema_version", nullable = false, length = 60)
    private String schemaVersion;

    @Column(name = "migration_files", nullable = false, columnDefinition = "text[]")
    private String[] migrationFiles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;
}
