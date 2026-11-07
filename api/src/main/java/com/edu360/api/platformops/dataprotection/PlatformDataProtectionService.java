package com.edu360.api.platformops.dataprotection;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.CreateRetentionChangeRequest;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DeletionRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.ExportRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.RetentionChangeRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.RetentionPolicyResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatformDataProtectionService {

    private final ExportRequestRepository exportRequestRepository;
    private final DeletionRequestRepository deletionRequestRepository;
    private final TenantRetentionPolicyRepository retentionPolicyRepository;
    private final RetentionChangeRequestRepository retentionChangeRequestRepository;
    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public ExportRequestResponse exportTenant(UUID tenantId, PlatformPrincipal principal) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        Instant now = Instant.now();
        ExportRequest req = ExportRequest.builder()
                .exportRequestId(UUID.randomUUID())
                .tenantId(tenantId)
                .requestedByUserId(principal.platformUserId())
                .requestedByKind("PRODUCT_OWNER")
                .status("READY")
                .fileRef(UUID.randomUUID())
                .downloadExpiresAt(now.plus(Duration.ofDays(7)))
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        ExportRequest saved = exportRequestRepository.save(req);
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Tenant export initiated for " + tenant.getTenantName());

        return new ExportRequestResponse(
                saved.getExportRequestId(),
                saved.getTenantId(),
                saved.getRequestedByKind(),
                saved.getStatus(),
                saved.getFileRef(),
                saved.getDownloadExpiresAt(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ExportRequestResponse> listExports(UUID tenantId) {
        return exportRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(r -> new ExportRequestResponse(
                        r.getExportRequestId(),
                        r.getTenantId(),
                        r.getRequestedByKind(),
                        r.getStatus(),
                        r.getFileRef(),
                        r.getDownloadExpiresAt(),
                        r.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public DeletionRequestResponse deleteTenant(UUID tenantId, String origin, PlatformPrincipal principal) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        Instant now = Instant.now();
        tenant.setState(TenantState.DELETED);
        tenant.setDeletedAt(now);
        tenant.setUpdatedAt(now);
        tenant.setUpdatedBy(principal.platformUserId());
        tenantRepository.save(tenant);

        DeletionRequest del = DeletionRequest.builder()
                .deletionRequestId(UUID.randomUUID())
                .tenantId(tenantId)
                .origin(origin != null ? origin : "PRODUCT_OWNER")
                .requestedByUserId(principal.platformUserId())
                .status("EXECUTED")
                .poConfirmedBy(principal.platformUserId())
                .poConfirmedAt(now)
                .deletedAt(now)
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        DeletionRequest saved = deletionRequestRepository.save(del);
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Tenant deleted: " + tenant.getTenantName());

        return new DeletionRequestResponse(
                saved.getDeletionRequestId(),
                saved.getTenantId(),
                saved.getOrigin(),
                saved.getStatus(),
                saved.getPoConfirmedAt(),
                saved.getRetentionEndsAt(),
                saved.getDeletedAt(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<RetentionPolicyResponse> getRetentionPolicies(UUID tenantId) {
        List<TenantRetentionPolicy> policies = retentionPolicyRepository.findByTenantIdAndStatus(tenantId, "ACTIVE");
        if (policies.isEmpty()) {
            Instant now = Instant.now();
            List<TenantRetentionPolicy> defaults = List.of(
                    new TenantRetentionPolicy(UUID.randomUUID(), tenantId, "STUDENT_RECORDS", 84, now, "ACTIVE", now, UUID.randomUUID(), now, UUID.randomUUID(), 1),
                    new TenantRetentionPolicy(UUID.randomUUID(), tenantId, "FINANCIAL_RECORDS", 96, now, "ACTIVE", now, UUID.randomUUID(), now, UUID.randomUUID(), 1),
                    new TenantRetentionPolicy(UUID.randomUUID(), tenantId, "AUDIT_LOGS", 36, now, "ACTIVE", now, UUID.randomUUID(), now, UUID.randomUUID(), 1)
            );
            return defaults.stream().map(p -> new RetentionPolicyResponse(
                    p.getTenantRetentionPolicyId(), p.getTenantId(), p.getDataClassCode(), p.getRetentionMonths(), p.getEffectiveFrom(), p.getStatus()
            )).toList();
        }
        return policies.stream()
                .map(p -> new RetentionPolicyResponse(
                        p.getTenantRetentionPolicyId(),
                        p.getTenantId(),
                        p.getDataClassCode(),
                        p.getRetentionMonths(),
                        p.getEffectiveFrom(),
                        p.getStatus()
                ))
                .toList();
    }

    @Transactional
    public RetentionChangeRequestResponse requestRetentionChange(UUID tenantId, CreateRetentionChangeRequest req, PlatformPrincipal principal) {
        Instant now = Instant.now();
        RetentionChangeRequest change = RetentionChangeRequest.builder()
                .retentionChangeRequestId(UUID.randomUUID())
                .tenantId(tenantId)
                .dataClassCode(req.dataClassCode())
                .currentMonths(84)
                .requestedMonths(req.requestedMonths())
                .reason(req.reason())
                .requestedBy(principal.platformUserId())
                .status("REQUESTED")
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        RetentionChangeRequest saved = retentionChangeRequestRepository.save(change);
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Retention change requested for " + req.dataClassCode() + " to " + req.requestedMonths() + " months");

        return new RetentionChangeRequestResponse(
                saved.getRetentionChangeRequestId(),
                saved.getTenantId(),
                saved.getDataClassCode(),
                saved.getCurrentMonths(),
                saved.getRequestedMonths(),
                saved.getReason(),
                saved.getStatus(),
                saved.getDecidedAt(),
                saved.getCreatedAt()
        );
    }
}
