package com.edu360.api.platformops.dataprotection.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class DataProtectionDtos {

    private DataProtectionDtos() {}

    public record ExportRequestResponse(
            UUID exportRequestId,
            UUID tenantId,
            String tenantName,
            String requestedByKind,
            String status,
            UUID fileRef,
            Instant downloadExpiresAt,
            Instant createdAt,
            Integer rowVersion
    ) {}

    public record DeletionRequestResponse(
            UUID deletionRequestId,
            UUID tenantId,
            String tenantName,
            String origin,
            String status,
            Instant poConfirmedAt,
            LocalDate retentionEndsAt,
            Instant deletedAt,
            Instant createdAt,
            String purgeStatus,
            Instant dpoDecidedAt,
            String dpoRemark,
            Instant retentionWaivedAt,
            String retentionWaiverReason,
            Integer rowVersion
    ) {}

    public record RetentionPolicyResponse(
            UUID tenantRetentionPolicyId,
            UUID tenantId,
            String dataClassCode,
            Integer retentionMonths,
            Instant effectiveFrom,
            String status
    ) {}

    public record RetentionChangeRequestResponse(
            UUID retentionChangeRequestId,
            UUID tenantId,
            String tenantName,
            String dataClassCode,
            Integer currentMonths,
            Integer requestedMonths,
            String reason,
            String status,
            Instant decidedAt,
            Instant createdAt,
            Integer rowVersion
    ) {}

    public record CreateRetentionChangeRequest(
            @NotBlank(message = "dataClassCode is required") String dataClassCode,
            @NotNull(message = "requestedMonths is required") @Positive(message = "requestedMonths must be positive") Integer requestedMonths,
            @NotBlank(message = "reason is required") String reason
    ) {}

    public record CreateDeletionRequestDto(
            @NotNull(message = "tenantId is required") UUID tenantId,
            String origin,
            String reason
    ) {}

    /** Optional body for the Product Owner confirmation (E5-S2, PD-7 waiver). No body means no waiver. */
    public record ConfirmDeletionRequestDto(
            boolean waiveRetentionWait,
            @Size(max = 900, message = "waiverReason must be at most 900 characters") String waiverReason
    ) {}

    public record DecideDeletionRequestDto(
            @NotBlank(message = "decision is required") String decision,
            String remark
    ) {}
}
