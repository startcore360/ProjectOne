package com.edu360.api.platformops.dataprotection;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileStorage;
import com.edu360.api.platformops.role.PlatformAuditWriter;

/**
 * E5-S3: the export request's file lifecycle (CL-09).
 *
 * WHAT: APPROVED → GENERATING (claimed by ExportJob) → READY with file_ref and a 24-hour download window, or
 * FAILED. ExpiryJob moves READY → EXPIRED once the window passes and deletes the stored ZIP; file_ref is kept,
 * because E5-S2 deletion readiness treats READY and EXPIRED as "the export was produced".
 * HOW: every status change runs in a read-write transaction on platformTransactionManager (platform.* tables)
 * and locks the row first, so parallel instances cannot claim or expire the same request twice. The stored
 * object key is derived from tenant id + file_ref (tenants/{tenantId}/{fileRef}); no column holds it.
 * WHY: a failed file delete must never block or undo the status change, so deletion happens after the status
 * is saved and recently expired requests are re-visited on later runs (delete is idempotent).
 */
@Service
public class ExportRequestLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ExportRequestLifecycleService.class);

    static final Duration DOWNLOAD_WINDOW = Duration.ofHours(24);
    static final int CLAIM_BATCH = 20;
    static final Duration DELETE_RETRY_WINDOW = Duration.ofDays(7);
    private static final String ACTION = "TENANT_EXPORT_APPROVE";

    /** A request ExportJob has claimed (now GENERATING) and must build. */
    public record ExportClaim(UUID exportRequestId, UUID tenantId) { }

    private final ExportRequestRepository repository;
    private final FileStorage fileStorage;
    private final PlatformAuditWriter auditWriter;

    public ExportRequestLifecycleService(ExportRequestRepository repository, FileStorage fileStorage,
            PlatformAuditWriter auditWriter) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.auditWriter = auditWriter;
    }

    static String storageKey(UUID tenantId, UUID fileRef) {
        return "tenants/" + tenantId + "/" + fileRef;
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public List<ExportClaim> claimApproved() {
        List<ExportClaim> claims = new ArrayList<>();
        for (ExportRequest request : repository.findForUpdateByStatus("APPROVED", PageRequest.of(0, CLAIM_BATCH))) {
            changeStatus(request, "GENERATING", "TENANT_EXPORT_GENERATING", PlatformAuditWriter.SUCCESS, Map.of());
            claims.add(new ExportClaim(request.getExportRequestId(), request.getTenantId()));
        }
        return claims;
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public void markReady(UUID exportRequestId, UUID fileRef) {
        ExportRequest request = repository.findByIdForUpdate(exportRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Export request not found: " + exportRequestId));
        if (!"GENERATING".equals(request.getStatus())) {
            throw new IllegalStateException("Export request " + exportRequestId + " is " + request.getStatus()
                    + ", not GENERATING; it cannot be marked READY.");
        }
        Instant expiresAt = Instant.now().plus(DOWNLOAD_WINDOW);
        request.setFileRef(fileRef);
        request.setDownloadExpiresAt(expiresAt);
        changeStatus(request, "READY", "TENANT_EXPORT_READY", PlatformAuditWriter.SUCCESS,
                Map.of("download_expires_at", change(null, expiresAt.toString())));
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public void markFailed(UUID exportRequestId, Exception cause) {
        ExportRequest request = repository.findByIdForUpdate(exportRequestId).orElse(null);
        if (request == null || !"GENERATING".equals(request.getStatus())) {
            return;
        }
        // Failure detail goes to the audit payload only (approved design: no new column, no row contents).
        String detail = cause == null ? "unknown" : cause.getClass().getSimpleName();
        changeStatus(request, "FAILED", "TENANT_EXPORT_FAILED", PlatformAuditWriter.FAILED,
                Map.of("failure", change(null, detail)));
    }

    /** READY → EXPIRED once the 24-hour window has passed, then the stored ZIP is deleted. */
    @Transactional(transactionManager = "platformTransactionManager")
    public int expireReadyExports() {
        Instant now = Instant.now();
        int expired = 0;
        for (ExportRequest request : repository.findReadyToExpireForUpdate(now)) {
            changeStatus(request, "EXPIRED", "TENANT_EXPORT_EXPIRED", PlatformAuditWriter.SUCCESS, Map.of());
            deleteQuietly(request);
            expired++;
        }
        // Retry: deletes are idempotent, so re-visit recently expired exports whose delete may have failed.
        for (ExportRequest request : repository.findByStatusAndUpdatedAtAfter("EXPIRED", now.minus(DELETE_RETRY_WINDOW))) {
            deleteQuietly(request);
        }
        return expired;
    }

    /**
     * Opens the stored ZIP for download. The caller (controller) has already checked the action
     * permission. Expired → 410 EXPORT_EXPIRED; not produced yet → EXPORT_NOT_READY. Audited as DOWNLOAD.
     */
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public InputStream openDownload(UUID exportRequestId, PlatformPrincipal principal) {
        ExportRequest request = repository.findById(exportRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Export request not found: " + exportRequestId));
        boolean expired = "EXPIRED".equals(request.getStatus())
                || ("READY".equals(request.getStatus()) && request.getDownloadExpiresAt() != null
                        && !request.getDownloadExpiresAt().isAfter(Instant.now()));
        if (expired) {
            throw new ApiException(HttpStatus.GONE, "EXPORT_EXPIRED",
                    "This export's 24-hour download window has ended. Request a new export.");
        }
        if (!"READY".equals(request.getStatus()) || request.getFileRef() == null) {
            throw new BusinessRuleViolationException("EXPORT_NOT_READY",
                    "This export is not ready to download (status " + request.getStatus() + ").");
        }
        InputStream stream = fileStorage.openRead(storageKey(request.getTenantId(), request.getFileRef()));
        auditWriter.record(principal, request.getTenantId(), "TENANT_EXPORT_DOWNLOADED", "DOWNLOAD",
                "TENANT_EXPORT_REQUEST", "EXPORT_REQUEST", exportRequestId, PlatformAuditWriter.SUCCESS);
        return stream;
    }

    private void deleteQuietly(ExportRequest request) {
        if (request.getFileRef() == null) return;
        try {
            fileStorage.delete(storageKey(request.getTenantId(), request.getFileRef()));
        } catch (RuntimeException ex) {
            log.error("Could not delete expired export file for request {}; will retry on the next run",
                    request.getExportRequestId(), ex);
        }
    }

    private void changeStatus(ExportRequest request, String next, String eventType, String outcome,
            Map<String, Map<String, Object>> extra) {
        String previous = request.getStatus();
        request.setStatus(next);
        request.setUpdatedAt(Instant.now());
        request.setUpdatedBy(PlatformAuditWriter.SYSTEM_ACTOR_ID);
        request.setRowVersion(request.getRowVersion() == null ? 1 : request.getRowVersion() + 1);
        repository.save(request);
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>(extra);
        changes.put("status", change(previous, next));
        auditWriter.recordSystem(request.getTenantId(), eventType, "EXPORT", ACTION, "EXPORT_REQUEST",
                request.getExportRequestId(), outcome, changes);
    }

    private static Map<String, Object> change(Object oldValue, Object newValue) {
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("old", oldValue);
        pair.put("new", newValue);
        return pair;
    }
}
