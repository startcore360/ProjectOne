package com.edu360.api.platformops.dataprotection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.edu360.api.platformops.PlatformNotificationService;
import com.edu360.api.platformops.PlatformConcurrency;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

import lombok.RequiredArgsConstructor;

/**
 * Tenant data-protection chains on the platform side (D1 Part II, D5, AP-23, CL-07).
 *
 * Deletion (L-01, decision PD-7 "mark now, purge later"): request, then the Product Owner confirms (PO_CONFIRMED,
 * an export request is linked or created), then the daily readiness job moves it to EXPORT_DONE once the export is
 * produced and to PENDING_DPO once the retention date has passed or the PO waived the wait (E5-S2), then the Data
 * Protection Officer decides. On approval the tenant is LOCKED at once
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
    /** Statuses the daily readiness job looks at (E5-S2). */
    static final List<String> READINESS_STATUSES = List.of("PO_CONFIRMED", "EXPORT_DONE");
    /** An existing export the deletion can wait on: still in progress, or produced and downloadable. */
    static final Set<String> LINKABLE_EXPORT_STATUSES = Set.of("REQUESTED", "APPROVED", "GENERATING", "READY");
    /**
     * The export has been produced. READY expires to EXPIRED after 24 h (E5-S3) while a deletion can wait months
     * for its retention date, so once produced the export gate stays met.
     */
    static final Set<String> PRODUCED_EXPORT_STATUSES = Set.of("READY", "EXPIRED");
    static final int MAX_WAIVER_REASON_LENGTH = 900;

    private final ExportRequestRepository exportRequestRepository;
    private final DeletionRequestRepository deletionRequestRepository;
    private final TenantRetentionPolicyRepository retentionPolicyRepository;
    private final RetentionChangeRequestRepository retentionChangeRequestRepository;
    private final TenantRepository tenantRepository;
    private final PlatformAuditWriter auditWriter;
    private final PlatformNotificationService notificationService;
    private final PlatformConcurrency platformConcurrency;
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
    public ExportRequestResponse getExportRequest(UUID exportRequestId) {
        ExportRequest request = exportRequestRepository.findById(exportRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Export request not found: " + exportRequestId));
        return toExportResponse(request);
    }

    @Transactional(readOnly = true)
    public List<ExportRequestResponse> listExports(UUID tenantId) {
        return exportRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toExportResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExportRequestResponse> listAllExports(String status, UUID tenantId) {
        String normalizedStatus = status == null || status.isBlank()
                ? null : status.trim().toUpperCase(java.util.Locale.ROOT);
        List<ExportRequest> requests;
        if (normalizedStatus != null && tenantId != null) {
            requests = exportRequestRepository.findByStatusAndTenantIdOrderByCreatedAtDesc(normalizedStatus, tenantId);
        } else if (normalizedStatus != null) {
            requests = exportRequestRepository.findByStatusOrderByCreatedAtDesc(normalizedStatus);
        } else if (tenantId != null) {
            requests = exportRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        } else {
            requests = exportRequestRepository.findAllByOrderByCreatedAtDesc();
        }
        return requests.stream().map(this::toExportResponse).toList();
    }

    /**
     * DPO decision only. Approval records state and notifies the requester; the A5 worker polls
     * APPROVED requests. This method deliberately does not create or enqueue an export job.
     */
    @Transactional
    public ExportRequestResponse decideExportRequest(UUID exportRequestId, String decision, String remark,
            String ifMatch, PlatformPrincipal principal) {
        ExportRequest request = exportRequestRepository.findById(exportRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Export request not found: " + exportRequestId));
        platformConcurrency.requireIfMatch(ifMatch, request.getRowVersion() == null ? 0 : request.getRowVersion());
        if (!"REQUESTED".equals(request.getStatus())) {
            throw new BusinessRuleViolationException("ALREADY_ACTIONED",
                    "This export request was already decided (" + request.getStatus() + ").");
        }
        if (request.getRequestedByUserId() != null
                && request.getRequestedByUserId().equals(principal.platformUserId())) {
            throw new UnauthorizedException("Separation of duties: the requester cannot decide their own export request.");
        }

        String upperDecision = decision != null ? decision.trim().toUpperCase(java.util.Locale.ROOT) : "";
        boolean approved = "APPROVED".equals(upperDecision);
        if (!approved && !"REJECTED".equals(upperDecision)) {
            throw new BusinessRuleViolationException("INVALID_DECISION", "Decision must be APPROVED or REJECTED.");
        }
        if (!approved && (remark == null || remark.isBlank())) {
            throw new BusinessRuleViolationException("REMARK_REQUIRED", "A rejection remark is mandatory.");
        }

        Instant now = Instant.now();
        request.setStatus(upperDecision);
        request.setDecidedBy(principal.platformUserId());
        request.setDecisionRemark(remark == null || remark.isBlank() ? null : remark.trim());
        request.setUpdatedAt(now);
        request.setUpdatedBy(principal.platformUserId());
        request.setRowVersion(request.getRowVersion() == null ? 1 : request.getRowVersion() + 1);
        ExportRequest saved = exportRequestRepository.save(request);

        auditWriter.record(principal, saved.getTenantId(),
                approved ? "TENANT_EXPORT_APPROVED" : "TENANT_EXPORT_REJECTED",
                approved ? "APPROVE" : "REJECT", "TENANT_EXPORT_APPROVE",
                "EXPORT_REQUEST", saved.getExportRequestId(), PlatformAuditWriter.SUCCESS);
        notificationService.notify(saved.getRequestedByUserId(),
                approved ? "EXPORT_REQUEST_APPROVED" : "EXPORT_REQUEST_REJECTED",
                approved ? "Tenant export approved" : "Tenant export rejected",
                approved
                        ? "Your export request was approved. The export worker will pick up this APPROVED request."
                        : "Your export request was rejected. Review the decision remark for details.",
                "EXPORT_REQUEST", saved.getExportRequestId(), "NORMAL", principal.platformUserId());
        return toExportResponse(saved);
    }

    private ExportRequestResponse toExportResponse(ExportRequest r) {
        return new ExportRequestResponse(r.getExportRequestId(), r.getTenantId(), tenantName(r.getTenantId()),
                r.getRequestedByKind(), r.getStatus(),
                r.getFileRef(), r.getDownloadExpiresAt(), r.getCreatedAt(), r.getRowVersion());
    }

    private String tenantName(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getTenantName)
                .orElse("Unknown tenant");
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
        return confirmTenantDeletion(deletionRequestId, false, null, principal);
    }

    /**
     * E8-S5: D5 confirmTenantDeletion requires If-Match. The version is checked against the stored row
     * first (428 missing, 400 malformed, 409 stale), then the normal confirmation runs in the same transaction.
     */
    @Transactional
    public DeletionRequestResponse confirmTenantDeletion(UUID deletionRequestId, boolean waiveRetentionWait,
            String waiverReason, PlatformPrincipal principal, String ifMatch) {
        DeletionRequest req = deletionRequestRepository.findById(deletionRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Deletion request not found: " + deletionRequestId));
        platformConcurrency.requireIfMatch(ifMatch, req.getRowVersion() == null ? 0 : req.getRowVersion());
        return confirmTenantDeletion(deletionRequestId, waiveRetentionWait, waiverReason, principal);
    }

    /**
     * E5-S2: the Product Owner confirms. The request becomes PO_CONFIRMED (not PENDING_DPO); the daily
     * {@link DeletionReadinessJob} moves it on once the export is produced and the retention date has passed.
     * PD-7 exception: on a PRODUCT_OWNER request the PO may waive the retention wait with a reason. The waiver
     * only skips the date gate; the export gate, the DPO-only decision and separation of duties still apply.
     */
    @Transactional
    public DeletionRequestResponse confirmTenantDeletion(UUID deletionRequestId, boolean waiveRetentionWait,
            String waiverReason, PlatformPrincipal principal) {
        DeletionRequest req = deletionRequestRepository.findById(deletionRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Deletion request not found: " + deletionRequestId));

        if (!"REQUESTED".equalsIgnoreCase(req.getStatus())) {
            throw new BusinessRuleViolationException("INVALID_STATUS",
                    "Deletion request must be in REQUESTED status to confirm (current: " + req.getStatus() + ")");
        }

        String reason = waiverReason == null ? "" : waiverReason.trim();
        if (waiveRetentionWait) {
            if (!"PRODUCT_OWNER".equals(req.getOrigin())) {
                throw new BusinessRuleViolationException("RETENTION_WAIVER_NOT_ALLOWED",
                        "The retention wait can only be waived on a deletion request raised by the Product Owner.");
            }
            if (reason.isEmpty()) {
                throw new BusinessRuleViolationException("REMARK_REQUIRED",
                        "A reason is required to waive the retention wait.");
            }
            if (reason.length() > MAX_WAIVER_REASON_LENGTH) {
                throw new BusinessRuleViolationException("REMARK_TOO_LONG",
                        "The waiver reason must be at most " + MAX_WAIVER_REASON_LENGTH + " characters.");
            }
        } else if (!reason.isEmpty()) {
            throw new BusinessRuleViolationException("INVALID_RETENTION_WAIVER",
                    "A waiver reason can only be given when the retention wait is waived.");
        }

        Instant now = Instant.now();
        // The date comes from the tenant's own retention setting. With none set (OD-06 durations are not
        // decided yet) we refuse rather than invent a date.
        LocalDate retentionEndsAt = retentionEndDate(req.getTenantId(), now);

        // Reuse the tenant's latest export only while it can still become (or already is) the deletion export;
        // a rejected, failed or expired one would leave the request waiting forever, so start a new one.
        List<ExportRequest> exports = exportRequestRepository.findByTenantIdOrderByCreatedAtDesc(req.getTenantId());
        UUID exportId = !exports.isEmpty() && LINKABLE_EXPORT_STATUSES.contains(exports.get(0).getStatus())
                ? exports.get(0).getExportRequestId()
                : exportTenant(req.getTenantId(), principal).exportRequestId();

        req.setExportRequestId(exportId);
        req.setRetentionEndsAt(retentionEndsAt);
        req.setPoConfirmedBy(principal.platformUserId());
        req.setPoConfirmedAt(now);
        if (waiveRetentionWait) {
            req.setRetentionWaivedBy(principal.platformUserId());
            req.setRetentionWaivedAt(now);
            req.setRetentionWaiverReason(reason);
        }
        req.setStatus("PO_CONFIRMED");
        req.setUpdatedAt(now);
        req.setUpdatedBy(principal.platformUserId());
        req.setRowVersion(req.getRowVersion() + 1);

        DeletionRequest saved = deletionRequestRepository.save(req);
        auditWriter.record(principal, saved.getTenantId(), "TENANT_DELETION_CONFIRMED", "APPROVE",
                "TENANT_DELETION_CONFIRM", "DELETION_REQUEST", deletionRequestId, PlatformAuditWriter.SUCCESS,
                Map.of("status", Map.of("old", "REQUESTED", "new", "PO_CONFIRMED"),
                        "retention_waived", Map.of("old", false, "new", waiveRetentionWait)));
        return toDeletionResponse(saved);
    }

    /**
     * E5-S2 daily readiness sweep (AP-23). Two separate gates:
     * PO_CONFIRMED → EXPORT_DONE once the linked export has been produced;
     * EXPORT_DONE → PENDING_DPO once {@code retention_ends_at <= today} or a Product Owner waiver is recorded.
     * Both steps can happen in one run. Returns the number of status changes made.
     * Runs on platformTransactionManager: deletion_request is a platform.* table, and its FOR UPDATE lock
     * must be held by a read-write platform transaction (a plain @Transactional would use the tenant one).
     */
    @Transactional(transactionManager = "platformTransactionManager")
    public int advanceDeletionReadiness(LocalDate today) {
        int transitions = 0;
        for (DeletionRequest request : deletionRequestRepository.findReadinessCandidatesForUpdate(READINESS_STATUSES)) {
            if ("PO_CONFIRMED".equals(request.getStatus())) {
                if (!exportProduced(request)) {
                    continue;
                }
                moveReadiness(request, "EXPORT_DONE", "TENANT_DELETION_EXPORT_DONE");
                transitions++;
            }
            if ("EXPORT_DONE".equals(request.getStatus()) && (retentionMet(request, today) || hasWaiver(request))) {
                moveReadiness(request, "PENDING_DPO", "TENANT_DELETION_READY_FOR_DPO");
                transitions++;
            }
        }
        return transitions;
    }

    private void moveReadiness(DeletionRequest request, String nextStatus, String eventType) {
        String previous = request.getStatus();
        request.setStatus(nextStatus);
        request.setUpdatedAt(Instant.now());
        request.setUpdatedBy(PlatformAuditWriter.SYSTEM_ACTOR_ID);
        request.setRowVersion(request.getRowVersion() == null ? 1 : request.getRowVersion() + 1);
        DeletionRequest saved = deletionRequestRepository.save(request);
        auditWriter.recordSystem(saved.getTenantId(), eventType, "UPDATE", "TENANT_DELETION_CONFIRM",
                "DELETION_REQUEST", saved.getDeletionRequestId(), PlatformAuditWriter.SUCCESS,
                Map.of("status", Map.of("old", previous, "new", nextStatus)));
    }

    private boolean exportProduced(DeletionRequest request) {
        return request.getExportRequestId() != null
                && exportRequestRepository.findById(request.getExportRequestId())
                        .map(e -> PRODUCED_EXPORT_STATUSES.contains(e.getStatus()))
                        .orElse(false);
    }

    private static boolean retentionMet(DeletionRequest request, LocalDate today) {
        return request.getRetentionEndsAt() != null && !request.getRetentionEndsAt().isAfter(today);
    }

    private static boolean hasWaiver(DeletionRequest request) {
        return "PRODUCT_OWNER".equals(request.getOrigin())
                && request.getRetentionWaivedAt() != null
                && request.getRetentionWaiverReason() != null
                && !request.getRetentionWaiverReason().isBlank();
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
        if (remark != null && remark.trim().length() > 1000) {
            throw new BusinessRuleViolationException("REMARK_TOO_LONG", "The decision remark must be at most 1000 characters.");
        }

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
            // Defence in depth: PENDING_DPO is normally reached only through the readiness job.
            if (!exportProduced(req)) {
                throw new BusinessRuleViolationException("EXPORT_NOT_READY",
                        "The tenant export must be produced before deletion can be approved.");
            }
            if (!retentionMet(req, LocalDate.now(ZoneOffset.UTC)) && !hasWaiver(req)) {
                throw new BusinessRuleViolationException("RETENTION_NOT_MET",
                        "The retention period has not ended and no Product Owner waiver is recorded.");
            }
            req.setDpoRemark(remark == null || remark.isBlank() ? null : remark.trim());
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
                tenantName(r.getTenantId()),
                r.getOrigin(),
                r.getStatus(),
                r.getPoConfirmedAt(),
                r.getRetentionEndsAt(),
                r.getDeletedAt(),
                r.getCreatedAt(),
                purgeStatus,
                r.getDpoDecidedAt(),
                r.getDpoRemark(),
                r.getRetentionWaivedAt(),
                r.getRetentionWaiverReason(),
                r.getRowVersion()
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
        return new RetentionChangeRequestResponse(r.getRetentionChangeRequestId(), r.getTenantId(),
                tenantName(r.getTenantId()), r.getDataClassCode(),
                r.getCurrentMonths(), r.getRequestedMonths(), r.getReason(), r.getStatus(), r.getDecidedAt(), r.getCreatedAt(),
                r.getRowVersion());
    }
}
