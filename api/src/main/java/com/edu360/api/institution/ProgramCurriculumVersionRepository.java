package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramCurriculumVersionRepository
        extends JpaRepository<ProgramCurriculumVersion, ProgramCurriculumVersionId> {

    List<ProgramCurriculumVersion> findByTenantIdAndInstitutionIdAndProgramIdOrderByVersionNumberDesc(
            UUID tenantId, UUID institutionId, UUID programId);

    Optional<ProgramCurriculumVersion> findByTenantIdAndInstitutionIdAndProgramIdAndProgramCurriculumVersionId(
            UUID tenantId, UUID institutionId, UUID programId, UUID programCurriculumVersionId);

    @Query("select coalesce(max(v.versionNumber), 0) from ProgramCurriculumVersion v where v.tenantId = :tenantId and v.programId = :programId")
    int findMaxVersionNumber(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId);
}
