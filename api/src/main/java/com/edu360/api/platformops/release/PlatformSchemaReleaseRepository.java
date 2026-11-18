package com.edu360.api.platformops.release;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformSchemaReleaseRepository extends JpaRepository<PlatformSchemaRelease, UUID> {
    Optional<PlatformSchemaRelease> findByReleaseIdAndSchemaVersion(UUID releaseId, String schemaVersion);

    @Query(value = """
            SELECT file_name || ':' || checksum_sha256
              FROM platform.migration_run
             WHERE release_id = :releaseId
               AND outcome = 'SUCCEEDED'
             GROUP BY file_name, checksum_sha256
             ORDER BY MIN(started_at), file_name
            """, nativeQuery = true)
    List<String> findAppliedMigrationFilesByReleaseId(@Param("releaseId") UUID releaseId);
}
