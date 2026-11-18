package com.edu360.api.platformops.dataprotection;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileStorage;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.platformops.PlatformNotificationService;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DeletionRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.ExportRequestResponse;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

import lombok.RequiredArgsConstructor;

/**
 * E5-S6 tenant-side data-protection operations.
 *
 * Tenant identity is supplied by the authenticated TenantPrincipal at the controller boundary.
 * Every history and request-id lookup is constrained to that tenant. A request id belonging to
 * another tenant is deliberately indistinguishable from a missing id (404).
 *
 * IMPORTANT IDENTITY DISTINCTION: requested_by_user_id stores the TENANT user account id from
 * TenantPrincipal, never a platform_user_id. created_by/updated_by on platform-owned rows use
 * the reserved system actor because those audit columns belong to the platform identity domain.
 *
 * This service creates requests and exposes tenant history/download only. Product Owner
 * confirmation and DPO decisions remain exclusively in the existing platform workflow.
 */
@Service
@RequiredArgsConstructor
public class TenantDataProtectionService {

    private static final List<String> OPEN_DELETION_STATUSES =
            List.of("REQUESTED", "PO_CONFIRMED", "EXPORT_DONE", "PENDING_DPO", "APPROVED");

    private final ExportRequestRepository exportRequestRepository;
    private final DeletionRequestRepository deletionRequestRepository;
    private final TenantRepository tenantRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformNotificationService notificationService;
    private final PlatformAuditWriter auditWriter;
    private final FileStorage fileStorage;

    @Transactional(transactionManager = "platformTransactionManager")
    public ExportRequestResponse requestExport(UUID tenantId, UUID tenantUserId) {
        requireExistingTenant(tenantId);
        Instant now = Instant.now();
        UUID requestId = UUID.randomUUID();

        ExportRequest saved = exportRequestRepository.save(ExportRequest.builder()
                .exportRequestId(requestId)
                .tenantId(tenantId)
                // This is a TENANT user id, not a platform user id.
                .requestedByUserId(tenantUserId)
                .requestedByKind("TENANT_ADMIN")
                .status("REQUESTED")
                .createdAt(now)
                .createdBy(PlatformAuditWriter.SYSTEM_ACTOR_ID)
                .updatedAt(now)
                .updatedBy(PlatformAuditWriter.SYSTEM_ACTOR_ID)
                .rowVersion(1)
                .build());

        auditWriter.recordSystem(tenantId, "TENANT_EXPORT_REQUESTED", "CREATE", "TENANT_EXPORT_REQUEST",
                "EXPORT_REQUEST", requestId, PlatformAuditWriter.SUCCESS, java.util.Map.of());
        notifyActiveUsers(PlatformRole.DATA_PROTECTION_OFFICER, "TENANT_EXPORT_REQUESTED",
                "Tenant export request needs a decision",
                "A tenant administrator has requested a data export. Review the request in Data Protection.",
                "EXPORT_REQUEST", requestId);
        return toExportResponse(saved);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public DeletionRequestResponse requestDeletion(UUID tenantId, UUID tenantUserId) {
        Tenant tenant = requireExistingTenant(tenantId);
        if (tenant.getState() == TenantState.DELETED) {
            throw new BusinessRuleViolationException("ALREADY_DELETED",
                    "This tenant is already deleted.");
        }
        if (deletionRequestRepository.existsByTenantIdAndStatusIn(tenantId, OPEN_DELETION_STATUSES)) {
            throw new BusinessRuleViolationException("DELETION_ALREADY_REQUESTED",
                    "This tenant already has an open deletion request.");
        }

        Instant now = Instant.now();
        UUID requestId = UUID.randomUUID();
        DeletionRequest saved = deletionRequestRepository.save(DeletionRequest.builder()
                .deletionRequestId(requestId)
                .tenantId(tenantId)
                .origin("TENANT_ADMIN")
                // This is a TENANT user id, not a platform user id.
                .requestedByUserId(tenantUserId)
                .status("REQUESTED")
                .createdAt(now)
                .createdBy(PlatformAuditWriter.SYSTEM_ACTOR_ID)
                .updatedAt(now)
                .updatedBy(PlatformAuditWriter.SYSTEM_ACTOR_ID)
                .rowVersion(1)
                .build());

        auditWriter.recordSystem(tenantId, "TENANT_DELETION_REQUESTED", "CREATE", "TENANT_DELETION_REQUEST",
                "DELETION_REQUEST", requestId, PlatformAuditWriter.SUCCESS, java.util.Map.of());
        notifyActiveUsers(PlatformRole.PRODUCT_OWNER, "TENANT_DELETION_REQUESTED",
                "Tenant deletion request needs confirmation",
                "A tenant administrator has requested tenant deletion. Product Owner confirmation is required.",
                "DELETION_REQUEST", requestId);
        return toDeletionResponse(saved);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public List<ExportRequestResponse> listExports(UUID tenantId) {
        List<ExportRequest> requests = exportRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        auditWriter.recordSystem(tenantId, "TENANT_EXPORT_HISTORY_VIEWED", "VIEW_SENSITIVE",
                "TENANT_EXPORT_REQUEST", "EXPORT_REQUEST", null, PlatformAuditWriter.SUCCESS,
                java.util.Map.of());
        return requests.stream().map(this::toExportResponse).toList();
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public List<DeletionRequestResponse> listDeletions(UUID tenantId) {
        List<DeletionRequest> requests = deletionRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        auditWriter.recordSystem(tenantId, "TENANT_DELETION_HISTORY_VIEWED", "VIEW_SENSITIVE",
                "TENANT_DELETION_REQUEST", "DELETION_REQUEST", null, PlatformAuditWriter.SUCCESS,
                java.util.Map.of());
        return requests.stream().map(this::toDeletionResponse).toList();
    }

    /**
     * Tenant-scoped E5-S3 download. The tenant-filtered lookup prevents request-id probing across
     * tenants. READY exports are downloadable only inside the original 24-hour window.
     */
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public InputStream openDownload(UUID tenantId, UUID exportRequestId) {
        ExportRequest request = exportRequestRepository.findByExportRequestIdAndTenantId(exportRequestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Export request not found."));

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

        InputStream stream = fileStorage.openRead(
                ExportRequestLifecycleService.storageKey(tenantId, request.getFileRef()));
        auditWriter.recordSystem(tenantId, "TENANT_EXPORT_DOWNLOADED", "DOWNLOAD",
                "TENANT_EXPORT_REQUEST", "EXPORT_REQUEST", exportRequestId, PlatformAuditWriter.SUCCESS,
                java.util.Map.of());
        return stream;
    }

    private Tenant requireExistingTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
    }

    private void notifyActiveUsers(PlatformRole role, String triggerCode, String title, String body,
            String entityType, UUID entityId) {
        List<PlatformUser> recipients = platformUserRepository.findAllByPlatformRoleAndStatus(
                role, PlatformUserStatus.ACTIVE);
        for (PlatformUser recipient : recipients) {
            notificationService.notify(recipient.getPlatformUserId(), triggerCode, title, body,
                    entityType, entityId, "HIGH", PlatformAuditWriter.SYSTEM_ACTOR_ID);
        }
    }

    private ExportRequestResponse toExportResponse(ExportRequest request) {
        return new ExportRequestResponse(request.getExportRequestId(), request.getTenantId(),
                tenantName(request.getTenantId()), request.getRequestedByKind(), request.getStatus(),
                request.getFileRef(), request.getDownloadExpiresAt(), request.getCreatedAt(),
                request.getRowVersion());
    }

    private DeletionRequestResponse toDeletionResponse(DeletionRequest request) {
        String purgeStatus = "APPROVED".equals(request.getStatus()) ? "PURGE_PENDING" : null;
        return new DeletionRequestResponse(request.getDeletionRequestId(), request.getTenantId(),
                tenantName(request.getTenantId()), request.getOrigin(), request.getStatus(),
                request.getPoConfirmedAt(), request.getRetentionEndsAt(), request.getDeletedAt(),
                request.getCreatedAt(), purgeStatus, request.getDpoDecidedAt(), request.getDpoRemark(),
                request.getRetentionWaivedAt(), request.getRetentionWaiverReason());
    }

    private String tenantName(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(Tenant::getTenantName).orElse("Unknown tenant");
    }
}
