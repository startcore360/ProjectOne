package com.edu360.api.platformops.dataprotection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantIssuerAuthenticationManagerResolver;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.CreateRetentionChangeRequest;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DeletionRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.ExportRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.RetentionChangeRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.RetentionPolicyResponse;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

import lombok.RequiredArgsConstructor;

/**
 * Tenant data-protection chains on the platform side (D1 Part II, D5, AP-23, CL-07).
 *
 * Deletion (L-01, decision PD-7 "mark now, purge later"): request, then the Product Owner confirms (an export
 * request is recorded), then the Data Protection Officer decides. On approval the tenant is LOCKED at once
 * (state DELETED: no sign-in, every API call refused, issuer cache evicted) and the request is marked APPROVED
 * with purgeStatus PURGE_PENDING until retentionEndsAt. The real purge job and the deletion certificate belong
 * to a later sprint, so no certificate is issued or mentioned here, and deleted_at stays empty.
 *
 * Retention (L-02, decision PD-3): a request starts from the tenant's real policy row, the DPO decides, and
 * the requester can never decide their own request (ck_retention_change_request_r3).
 *
 * Every step writes a named event to platform.audit_outbox through the required PlatformAuditWriter.
 */
@Service
@RequiredArgsConstructor
public class PlatformDataProtectionService {

    static final List<String> OPEN_DELETION_STATUSES = List.of("REQUESTED", "PO_CONFIRMED", "EXPORT_DONE", "PENDING_DPO", "APPROVED");

    private final ExportRequestRepository exportRequestRepository;
    private final DeletionRequestRepository deletionRequestRepository;
    private final TenantRetentionPolicyRepository retentionPolicyRepository;
    private final RetentionChangeRequestRepository retentionChangeRequestRepository;
    private final TenantRepository tenantRepository;
    private final PlatformAuditWriter auditWriter;
    private final TenantIssuerAuthenticationManagerResolver tenantIssuerResolver;

    /**
     * Records that an export was asked for. No file is generated yet (the export job is a later piece of
     * work), so the request stays REQUESTED with no file reference and no download link.
     */
    @Transactional
    public ExportRequestResponse exportTenant(UUID tenantId, PlatformPrincipal principal) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        Instant now = Instant.now();
        ExportRequest saved = exportRequestRepository.save(ExportRequest.builder()
                .exportRequestId(UUID.randomUUID())
                .tenantId(tenantId)
                .requestedByUserId(principal.platformUserId())
                .requestedByKind("PRODUCT_OWNER")
                .status("REQUESTED")
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build());
        auditWriter.record(principal, tenantId, "TENANT_EXPORT_REQUESTED", "TENANT_EXPORT_REQUEST", "EXPORT_REQUEST",
                saved.getExportRequestId());
        return toExportResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ExportRequestResponse> listExports(UUID tenantId) {
        return exportRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toExportResponse)
                .toList();
    }

    private ExportRequestResponse toExportResponse(ExportRequest r) {
        return new ExportRequestResponse(r.getExportRequestId(), r.getTenantId(), r.getRequestedByKind(), r.getStatus(),
                r.getFileRef(), r.getDownloadExpiresAt(), r.getCreatedAt());
    }

    // ------------------------------------------------------------------ deletion (L-01)

    @Transactional
    public DeletionRequestResponse requestTenantDeletionPlatform(UUID tenantId, String origin, PlatformPrincipal principal) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (tenant.getState() == TenantState.DELETED) {
            throw new BusinessRuleViolationException("ALREADY_DELETED", "Tenant " + tenantId + " is already deleted.");
        }
        if (deletionRequestRepository.existsByTenantIdAndStatusIn(tenantId, OPEN_DELETION_STATUSES)) {
            throw new BusinessRuleViolationException("DELETION_ALREADY_REQUESTED",
                    "Tenant " + tenantId + " already has an open deletion request.");
        }

        Instant now = Instant.now();
        DeletionRequest saved = deletionRequestRepository.save(DeletionRequest.builder()
                .deletionRequestId(UUID.randomUUID())
                .tenantId(tenantId)
                .origin(origin != null ? origin : "PRODUCT_OWNER")
                .requestedByUserId(principal.platformUserId())
                .status("REQUESTED")
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build());
        auditWriter.record(principal, tenantId, "TENANT_DELETION_REQUESTED", "TENANT_DELETION_REQUEST",
                "DELETION_REQUEST", saved.getDeletionRequestId());
        return toDeletionResponse(saved);
    }

    @Transactional
    public DeletionRequestResponse confirmTenantDeletion(UUID deletionRequestId, PlatformPrincipal principal) {
        DeletionRequest req = deletionRequestRepository.findById(deletionRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Deletion request not found: " + deletionRequestId));

        if (!"REQUESTED".equalsIgnoreCase(req.getStatus())) {
            throw new BusinessRuleViolationException("INVALID_STATUS",
                    "Deletion request must be in REQUESTED status to confirm (current: " + req.getStatus() + ")");
        }

        Instant now = Instant.now();
        // The date comes from the tenant's own retention setting. With none set (OD-06 durations are not
        // decided yet) we refuse rather than invent a date.
        LocalDate retentionEndsAt = retentionEndDate(req.getTenantId(), now);

        List<ExportRequest> exports = exportRequestRepository.findByTenantIdOrderByCreatedAtDesc(req.getTenantId());
        UUID exportId = exports.isEmpty()
                ? exportTenant(req.getTenantId(), principal).exportRequestId()
                : exports.get(0).getExportRequestId();

        req.setExportRequestId(exportId);
        req.setRetentionEndsAt(retentionEndsAt);
        req.setPoConfirmedBy(principal.platformUserId());
        req.setPoConfirmedAt(now);
        req.setStatus("PENDING_DPO");
        req.setUpdatedAt(now);
        req.setUpdatedBy(principal.platformUserId());
        req.setRowVersion(req.getRowVersion() + 1);

        DeletionRequest saved = deletionRequestRepository.save(req);
        auditWriter.record(principal, saved.getTenantId(), "TENANT_DELETION_CONFIRMED", "TENANT_DELETION_CONFIRM",
                "DELETION_REQUEST", deletionRequestId);
        return toDeletionResponse(saved);
    }

    /** Longest retention among the tenant's active policies, counted from {@code from}. */
    LocalDate retentionEndDate(UUID tenantId, Instant from) {
        int months = retentionPolicyRepository.findByTenantIdAndStatus(tenantId, "ACTIVE").stream()
                .map(TenantRetentionPolicy::getRetentionMonths)
                .filter(m -> m != null && m > 0)
                .mapToInt(Integer::intValue)
                .max()
                .orElseThrow(() -> new BusinessRuleViolationException("RETENTION_NOT_SET",
                        "This tenant has no retention period set, so the retention end date cannot be worked out. "
                                + "Set one first through a retention change request approved by the Data Protection Officer."));
        return from.atZone(ZoneOffset.UTC).toLocalDate().plusMonths(months);
    }

    @Transactional
    public DeletionRequestResponse decideTenantDeletion(UUID deletionRequestId, String decision, String remark, PlatformPrincipal principal) {
        DeletionRequest req = deletionRequestRepository.findById(deletionRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Deletion request not found: " + deletionRequestId));

        if (!"PENDING_DPO".equalsIgnoreCase(req.getStatus())) {
            throw new BusinessRuleViolationException("INVALID_STATUS",
                    "Deletion request must be in PENDING_DPO status to decide (current: " + req.getStatus() + ")");
        }

        // ck_deletion_request_r4: the person who confirmed cannot decide.
        if (principal.platformUserId().equals(req.getPoConfirmedBy())) {
            throw new BusinessRuleViolationException("SEPARATION_OF_DUTIES", "Product Owner who confirmed cannot decide: the person who confirmed this deletion request cannot decide it.");
        }

        Instant now = Instant.now();
        String upperDecision = decision != null ? decision.trim().toUpperCase() : "";

        if ("REJECTED".equals(upperDecision)) {
            if (remark == null || remark.trim().isBlank()) {
                throw new BusinessRuleViolationException("REMARK_REQUIRED", "Rejection remark is mandatory (1-1000 characters).");
            }
            req.setStatus("REJECTED");
            req.setDpoRemark(remark.trim());
            stampDecision(req, principal, now);
            DeletionRequest saved = deletionRequestRepository.save(req);
            auditWriter.record(principal, saved.getTenantId(), "TENANT_DELETION_REJECTED", "REJECT",
                    "TENANT_DELETION_APPROVE", "DELETION_REQUEST", deletionRequestId, PlatformAuditWriter.SUCCESS);
            return toDeletionResponse(saved);
        } else if ("APPROVED".equals(upperDecision)) {
            req.setStatus("APPROVED");
            stampDecision(req, principal, now);
            DeletionRequest saved = deletionRequestRepository.save(req);

            Tenant tenant = tenantRepository.findById(req.getTenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + req.getTenantId()));
            tenant.setState(TenantState.DELETED); // the lock: no sign-in, every API call refused
            tenant.setDeletedAt(now);
            tenant.setUpdatedAt(now);
            tenant.setUpdatedBy(principal.platformUserId());
            tenantRepository.save(tenant);
            if (tenant.getKeycloakRealm() != null) {
                tenantIssuerResolver.evictTenantIssuer(tenant.getKeycloakRealm());
            }

            auditWriter.record(principal, saved.getTenantId(), "TENANT_DELETION_APPROVED", "APPROVE",
                    "TENANT_DELETION_APPROVE", "DELETION_REQUEST", deletionRequestId, PlatformAuditWriter.SUCCESS);
            return toDeletionResponse(saved);
        } else {
            throw new BusinessRuleViolationException("INVALID_DECISION", "Decision must be APPROVED or REJECTED");
        }
    }

    private static void stampDecision(DeletionRequest req, PlatformPrincipal principal, Instant now) {
        req.setDpoDecidedBy(principal.platformUserId());
        req.setDpoDecidedAt(now);
        req.setUpdatedAt(now);
        req.setUpdatedBy(principal.platformUserId());
        req.setRowVersion(req.getRowVersion() + 1);
    }

    @Transactional(readOnly = true)
    public DeletionRequestResponse getDeletionRequest(UUID deletionRequestId) {
        return toDeletionResponse(deletionRequestRepository.findById(deletionRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Deletion request not found: " + deletionRequestId)));
    }

    @Transactional(readOnly = true)
    public List<DeletionRequestResponse> listDeletionRequests() {
        return deletionRequestRepository.findAll().stream().map(this::toDeletionResponse).toList();
    }

    private DeletionRequestResponse toDeletionResponse(DeletionRequest r) {
        // PD-7: approved means locked now and purged later; the purge itself has not been built, so say so.
        String purgeStatus = "APPROVED".equals(r.getStatus()) ? "PURGE_PENDING" : null;
        return new DeletionRequestResponse(
                r.getDeletionRequestId(),
                r.getTenantId(),
                r.getOrigin(),
                r.getStatus(),
                r.getPoConfirmedAt(),
                r.getRetentionEndsAt(),
                r.getDeletedAt(),
                r.getCreatedAt(),
                purgeStatus,
                r.getDpoDecidedAt(),
                r.getDpoRemark()
        );
    }

    // ------------------------------------------------------------------ retention (L-02)

    /** The tenant's real policy rows. An empty list means none is set yet (OD-06); nothing is made up. */
    @Transactional(readOnly = true)
    public List<RetentionPolicyResponse> getRetentionPolicies(UUID tenantId) {
        return retentionPolicyRepository.findByTenantIdAndStatus(tenantId, "ACTIVE").stream()
                .map(p -> new RetentionPolicyResponse(p.getTenantRetentionPolicyId(), p.getTenantId(),
                        p.getDataClassCode(), p.getRetentionMonths(), p.getEffectiveFrom(), p.getStatus()))
                .toList();
    }

    /** D5 requestRetentionChange (Product Owner). */
    @Transactional
    public RetentionChangeRequestResponse requestRetentionChange(UUID tenantId, CreateRetentionChangeRequest req, PlatformPrincipal principal) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
        Instant now = Instant.now();
        Integer current = retentionPolicyRepository
                .findByTenantIdAndDataClassCodeAndStatus(tenantId, req.dataClassCode(), "ACTIVE")
                .map(TenantRetentionPolicy::getRetentionMonths)
                .orElse(null);
        RetentionChangeRequest saved = retentionChangeRequestRepository.save(RetentionChangeRequest.builder()
                .retentionChangeRequestId(UUID.randomUUID())
                .tenantId(tenantId)
                .dataClassCode(req.dataClassCode())
                .currentMonths(current)
                .requestedMonths(req.requestedMonths())
                .reason(req.reason().trim())
                .requestedBy(principal.platformUserId())
                .status("REQUESTED")
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build());
        auditWriter.record(principal, tenantId, "RETENTION_CHANGE_REQUESTED", "CREATE", "TENANT_SETTINGS",
                "RETENTION_CHANGE_REQUEST", saved.getRetentionChangeRequestId(), PlatformAuditWriter.SUCCESS);
        return toRetentionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RetentionChangeRequestResponse> listRetentionChangeRequests() {
        return retentionChangeRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toRetentionResponse).toList();
    }

    /** D5 decideRetentionChange: Data Protection Officer only; the requester can never decide. */
    @Transactional
    public RetentionChangeRequestResponse decideRetentionChange(UUID requestId, String decision, String remark, PlatformPrincipal principal) {
        RetentionChangeRequest req = retentionChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Retention change request not found: " + requestId));

        if (!"REQUESTED".equals(req.getStatus())) {
            throw new BusinessRuleViolationException("ALREADY_ACTIONED",
                    "This retention change request was already decided (" + req.getStatus() + ").");
        }
        // ck_retention_change_request_r3
        if (principal.platformUserId().equals(req.getRequestedBy())) {
            throw new UnauthorizedException("Separation of duties: the person who requested a retention change cannot decide it.");
        }

        String upper = decision != null ? decision.trim().toUpperCase() : "";
        Instant now = Instant.now();
        if ("REJECTED".equals(upper)) {
            if (remark == null || remark.isBlank()) {
                throw new BusinessRuleViolationException("REMARK_REQUIRED", "Rejection remark is mandatory (1-1000 characters).");
            }
            req.setStatus("REJECTED");
            req.setDecisionRemark(remark.trim());
        } else if ("APPROVED".equals(upper)) {
            req.setStatus("APPROVED");
            applyApprovedRetention(req, principal, now);
        } else {
            throw new BusinessRuleViolationException("INVALID_DECISION", "Decision must be APPROVED or REJECTED");
        }
        req.setDecidedBy(principal.platformUserId());
        req.setDecidedAt(now);
        req.setUpdatedAt(now);
        req.setUpdatedBy(principal.platformUserId());
        req.setRowVersion(req.getRowVersion() + 1);
        RetentionChangeRequest saved = retentionChangeRequestRepository.save(req);

        boolean approved = "APPROVED".equals(saved.getStatus());
        auditWriter.record(principal, saved.getTenantId(),
                approved ? "RETENTION_CHANGE_APPROVED" : "RETENTION_CHANGE_REJECTED", approved ? "APPROVE" : "REJECT",
                "RETENTION_CHANGE_APPROVE", "RETENTION_CHANGE_REQUEST", saved.getRetentionChangeRequestId(),
                PlatformAuditWriter.SUCCESS);
        return toRetentionResponse(saved);
    }

    private void applyApprovedRetention(RetentionChangeRequest req, PlatformPrincipal principal, Instant now) {
        retentionPolicyRepository.findByTenantIdAndDataClassCodeAndStatus(req.getTenantId(), req.getDataClassCode(), "ACTIVE")
                .ifPresent(old -> {
                    old.setStatus("SUPERSEDED");
                    old.setUpdatedAt(now);
                    old.setUpdatedBy(principal.platformUserId());
                    retentionPolicyRepository.saveAndFlush(old);
                });
        retentionPolicyRepository.save(TenantRetentionPolicy.builder()
                .tenantRetentionPolicyId(UUID.randomUUID())
                .tenantId(req.getTenantId())
                .dataClassCode(req.getDataClassCode())
                .retentionMonths(req.getRequestedMonths())
                .effectiveFrom(now)
                .status("ACTIVE")
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build());
    }

    private RetentionChangeRequestResponse toRetentionResponse(RetentionChangeRequest r) {
        return new RetentionChangeRequestResponse(r.getRetentionChangeRequestId(), r.getTenantId(), r.getDataClassCode(),
                r.getCurrentMonths(), r.getRequestedMonths(), r.getReason(), r.getStatus(), r.getDecidedAt(), r.getCreatedAt());
    }
}
