package com.edu360.api.approval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.approval.dto.BulkApprovalRequest;
import com.edu360.api.approval.dto.BulkApprovalResponse;
import com.edu360.api.approval.dto.CreateDelegationRequest;
import com.edu360.api.approval.dto.DelegationResponse;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApprovalDelegationService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalDelegationService.class);

    private final DelegationRepository delegationRepository;
    private final ApprovalBatchRepository approvalBatchRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<DelegationResponse> listDelegations(UUID tenantId, UUID currentUserId) {
        return delegationRepository.findByIdTenantIdAndDelegatorUserId(tenantId, currentUserId)
                .stream()
                .map(DelegationResponse::from)
                .toList();
    }

    @Transactional
    public DelegationResponse createDelegation(UUID tenantId, TenantPrincipal principal, CreateDelegationRequest request) {
        // Fail-closed authorization check
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "APPROVAL_DELEGATE",
                RbacScope.tenant(),
                RbacTarget.NONE
        );

        // Addendum Rule 11 & Table Constraint: delegator cannot delegate to themselves
        if (principal.userAccountId().equals(request.delegateUserId())) {
            throw new BusinessRuleViolationException("SELF_DELEGATION_PROHIBITED",
                    "Delegator cannot delegate approval authority to themselves (Rule 11).");
        }

        // Table Constraint: ends_at > starts_at
        if (request.startsAt() == null || request.endsAt() == null || !request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessRuleViolationException("INVALID_DATE_RANGE",
                    "Delegation end time must be strictly after start time.");
        }

        // Rule WF-37 / C12: Merit-list approval can never be delegated
        if ("MERIT_LIST_APPROVE".equalsIgnoreCase(request.processCode())) {
            throw new BusinessRuleViolationException("MERIT_LIST_DELEGATION_PROHIBITED",
                    "Merit list approval can never be delegated (Rule WF-37 / C12).");
        }

        UUID delegationId = UUID.randomUUID();
        Delegation delegation = Delegation.builder()
                .id(new DelegationId(tenantId, delegationId))
                .delegatorUserId(principal.userAccountId())
                .delegateUserId(request.delegateUserId())
                .delegationBasis(request.delegationBasis())
                .processCode(request.processCode())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .status("ACTIVE")
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .rowVersion(1)
                .build();

        Delegation saved = delegationRepository.save(delegation);

        auditLogService.record(
                AuditEventType.APPROVAL_DELEGATED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Created delegation " + delegationId + " for delegate " + request.delegateUserId()
        );

        log.info("Delegation {} created by {} for delegate {} in tenant {}",
                delegationId, principal.userAccountId(), request.delegateUserId(), tenantId);

        return DelegationResponse.from(saved);
    }

    @Transactional
    public DelegationResponse cancelDelegation(UUID tenantId, UUID delegationId, TenantPrincipal principal) {
        Delegation delegation = delegationRepository.findByIdTenantIdAndIdDelegationId(tenantId, delegationId)
                .orElseThrow(() -> new ResourceNotFoundException("Delegation not found: " + delegationId));

        if (!delegation.getDelegatorUserId().equals(principal.userAccountId())) {
            // Must have APPROVAL_DELEGATE permission to cancel other people's delegations
            permissionEvaluatorService.authorize(
                    new RbacPrincipal(tenantId, principal.userAccountId()),
                    "APPROVAL_DELEGATE",
                    RbacScope.tenant(),
                    RbacTarget.NONE
            );
        }

        if ("CANCELLED".equals(delegation.getStatus())) {
            throw new BusinessRuleViolationException("DELEGATION_ALREADY_CANCELLED", "Delegation is already cancelled.");
        }

        delegation.setStatus("CANCELLED");
        delegation.setCancelledAt(Instant.now());
        delegation.setCancelledBy(principal.userAccountId());
        delegation.setUpdatedAt(Instant.now());
        delegation.setUpdatedBy(principal.userAccountId());
        delegation.setRowVersion(delegation.getRowVersion() != null ? delegation.getRowVersion() + 1 : 1);

        Delegation saved = delegationRepository.save(delegation);

        auditLogService.record(
                AuditEventType.APPROVAL_DELEGATION_CANCELLED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Cancelled delegation " + delegationId
        );

        log.info("Delegation {} cancelled by {} in tenant {}", delegationId, principal.userAccountId(), tenantId);

        return DelegationResponse.from(saved);
    }

    @Transactional
    public BulkApprovalResponse bulkDecide(UUID tenantId, TenantPrincipal principal, BulkApprovalRequest request) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "APPROVAL_BULK",
                RbacScope.tenant(),
                RbacTarget.NONE
        );

        if (request.approvalRequestIds() == null || request.approvalRequestIds().isEmpty()) {
            throw new BusinessRuleViolationException("EMPTY_BATCH", "At least one approval request ID is required for bulk decisions.");
        }

        // Rule C12: Data Protection Officer approvals are never bulk-approved
        if ("DATA_PROTECTION".equalsIgnoreCase(request.processCategory())) {
            throw new BusinessRuleViolationException("DPO_BULK_APPROVAL_PROHIBITED",
                    "Data Protection Officer approvals (exports, deletions, retention) are never bulk-approved [Rule C12].");
        }

        UUID batchId = UUID.randomUUID();
        ApprovalBatch batch = ApprovalBatch.builder()
                .id(new ApprovalBatchId(tenantId, batchId))
                .actedByUserId(principal.userAccountId())
                .actedActiveRole(principal.userType().name())
                .batchComment(request.comment())
                .itemCount(request.approvalRequestIds().size())
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .rowVersion(1)
                .build();

        approvalBatchRepository.save(batch);

        List<String> errors = new ArrayList<>();
        int succeeded = 0;

        for (UUID requestId : request.approvalRequestIds()) {
            try {
                // Rule AP-25: Each item is logged separately
                auditLogService.record(
                        AuditEventType.APPROVAL_BULK_DECIDED,
                        null,
                        principal.userAccountId().toString(),
                        tenantId.toString(),
                        "Bulk decided request " + requestId + ": " + request.decision()
                );
                succeeded++;
            } catch (Exception e) {
                errors.add("Failed to process approval " + requestId + ": " + e.getMessage());
            }
        }

        return new BulkApprovalResponse(
                batchId,
                request.approvalRequestIds().size(),
                succeeded,
                errors.size(),
                errors
        );
    }
}
