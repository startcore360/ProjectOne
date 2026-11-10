package com.edu360.api.imports.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.imports.ImportJob;

public record ImportJobResponse(
        UUID importJobId,
        String status,
        String importKind,
        UUID institutionId,
        Integer totalRows,
        Integer validRows,
        Integer errorRows,
        String importMode,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
    public static ImportJobResponse from(ImportJob job) {
        return new ImportJobResponse(
                job.getId().getImportJobId(),
                job.getStatus(),
                job.getImportKind(),
                job.getInstitutionId(),
                job.getTotalRows(),
                job.getValidRows(),
                job.getErrorRows(),
                job.getImportMode(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedAt()
        );
    }
}
