package com.edu360.api.approval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.approval.dto.BulkApprovalRequest;
import com.edu360.api.approval.dto.BulkApprovalResponse;
import com.edu360.api.approval.dto.CreateDelegationRequest;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.approval.dto.DelegationResponse;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.TenantContext;

/**
 * WHAT: approval delegation (P-07) and bulk approval (P-06).
 *
 * Delegation rules enforced here: the delegator holds APPROVAL_DELEGATE and the delegated action
 * at their OWN active scope; the delegate exists in this tenant, is ACTIVE and works inside the
 * delegator's scope; a person who is themselves acting by delegation cannot delegate on (no
 * re-delegation); merit list approval is never delegated (D2 action 20); only the delegator or an
 * authorised in-scope admin cancels. The effect of a delegation (who may decide, and the items
 * returning to the delegator when it ends) lives in {@link ApprovalRouting}.
 *
 * Bulk approval follows D5 bulkApprove: approvals only (rejects are never bulk); the whole batch is
 * refused before anything is decided when an item does not exist in the caller's scope (404) or is a
 * Data Protection Officer approval (422, D2 action 21); after that each item is decided on its own and
 * reported with a per-item outcome (APPROVED, ALREADY_ACTIONED, NOT_CURRENT_APPROVER,
 * NOT_BULK_ELIGIBLE), one failing item never undoing the others.
 */
@Service
public class ApprovalDelegationService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalDelegationService.class);

    private final DelegationRepository delegationRepository;
    private final ApprovalBatchRepository approvalBatchRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final UserAccountRepository userAccountRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalInboxService approvalInboxService;

    public ApprovalDelegationService(
            DelegationRepository delegationRepository,
            ApprovalBatchRepository approvalBatchRepository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService,
            UserAccountRepository userAccountRepository,
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalInboxService approvalInboxService) {
        this.delegationRepository = delegationRepository;
        this.approvalBatchRepository = approvalBatchRepository;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.auditLogService = auditLogService;
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.approvalRequestRepository = Objects.requireNonNull(approvalRequestRepository);
        this.approvalInboxService = Objects.requireNonNull(approvalInboxService);
    }

    @Transactional(readOnly = true)
    public List<DelegationResponse> listDelegations(UUID tenantId, UUID currentUserId) {
        return delegationRepository.findByIdTenantIdAndDelegatorUserId(tenantId, currentUserId)
                .stream()
                .map(DelegationResponse::from)
                .toList();
    }

    @Transactional
    public DelegationResponse createDelegation(UUID tenantId, TenantPrincipal principal, CreateDelegationRequest request) {
        // Fail-closed authorization check, at the delegator's own active scope (A-05).
        RbacPrincipal delegator = new RbacPrincipal(tenantId, principal.userAccountId());
        RbacScope delegatorScope = ownScope(delegator);
        permissionEvaluatorService.authorize(delegator, "APPROVAL_DELEGATE", delegatorScope, RbacTarget.NONE);

        // No re-delegation: someone who is acting by a running delegation cannot hand it on.
        Instant now = Instant.now();
        boolean actingByDelegation = delegationRepository.findByIdTenantIdAndDelegateUserId(tenantId, principal.userAccountId())
                .stream().anyMatch(d -> "ACTIVE".equals(d.getStatus())
                        && d.getStartsAt() != null && !d.getStartsAt().isAfter(now)
                        && d.getEndsAt() != null && d.getEndsAt().isAfter(now));
        if (actingByDelegation) {
            throw new BusinessRuleViolationException("REDELEGATION_PROHIBITED",
                    "A delegate cannot delegate the delegated approvals on to someone else.");
        }

        // Addendum Rule 11: delegator cannot delegate to themselves
        if (principal.userAccountId().equals(request.delegateUserId())) {
            throw new BusinessRuleViolationException("SELF_DELEGATION_PROHIBITED",
                    "Delegator cannot delegate approval authority to themselves (Rule 11).");
        }

        // Rule WF-37 / C12: Merit-list approval can never be delegated
        if ("MERIT_LIST_APPROVE".equalsIgnoreCase(request.processCode())) {
            throw new BusinessRuleViolationException("MERIT_LIST_DELEGATION_PROHIBITED",
                    "Merit list approval can never be delegated (Rule WF-37 / C12).");
        }

        // Table Constraint: ends_at > starts_at
        if (request.startsAt() == null || request.endsAt() == null || !request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessRuleViolationException("INVALID_DATE_RANGE",
                    "Delegation end time must be strictly after start time.");
        }

        // BS-16: the delegate exists in this tenant, is ACTIVE and works inside the delegator's scope
        UserAccount delegate = userAccountRepository.findById(new UserAccountId(tenantId, request.delegateUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Delegate user not found: " + request.delegateUserId()));
        if (delegate.getStatus() != UserAccountStatus.ACTIVE) {
            throw new BusinessRuleViolationException("DELEGATE_NOT_ACTIVE",
                    "The delegate's account is not active (status " + delegate.getStatus() + ").");
        }
        if (!inScope(tenantId, request.delegateUserId(), delegatorScope)) {
            throw new BusinessRuleViolationException("DELEGATE_OUT_OF_SCOPE",
                    "The delegate does not work in the delegator's institution or department.");
        }

        // BS-16: the delegator holds the delegated right, at their own active scope
        if (request.processCode() != null && !request.processCode().isBlank() && !"ALL".equalsIgnoreCase(request.processCode())) {
            var decision = permissionEvaluatorService.can(delegator, request.processCode(), delegatorScope, RbacTarget.NONE);
            if (decision == null || !decision.allowed()) {
                throw new BusinessRuleViolationException("DELEGATOR_LACKS_ACTION",
                        "Delegator does not hold permission " + request.processCode() + " to delegate.");
            }
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
                .createdAt(now)
                .updatedAt(now)
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

    /** True only when the delegate has a current assignment inside (or above) the delegator's scope. */
    private boolean inScope(UUID tenantId, UUID userId, RbacScope scope) {
        List<com.edu360.api.rbac.UserRoleAssignment> assignments =
                permissionEvaluatorService.heldAssignments(new RbacPrincipal(tenantId, userId));
        if (assignments.isEmpty()) {
            return false;
        }
        if (scope.scopeType() == ScopeType.TENANT) {
            return true;
        }
        return assignments.stream().anyMatch(a -> {
            if (a.getScopeType() == ScopeType.TENANT) {
                return true;
            }
            if (!Objects.equals(a.getInstitutionId(), scope.institutionId())) {
                return false;
            }
            if (scope.scopeType() == ScopeType.INSTITUTION) {
                return true;
            }
            return scope.scopeType() == ScopeType.DEPARTMENT
                    && (a.getScopeType() == ScopeType.INSTITUTION
                            || (a.getScopeType() == ScopeType.DEPARTMENT
                                    && Objects.equals(a.getDepartmentId(), scope.departmentId())));
        });
    }

    @Transactional
    public DelegationResponse cancelDelegation(UUID tenantId, UUID delegationId, TenantPrincipal principal) {
        Delegation delegation = delegationRepository.findByIdTenantIdAndIdDelegationId(tenantId, delegationId)
                .orElseThrow(() -> new ResourceNotFoundException("Delegation not found: " + delegationId));

        if (!delegation.getDelegatorUserId().equals(principal.userAccountId())) {
            // Must have APPROVAL_DELEGATE permission to cancel other people's delegations
            RbacPrincipal canceller = new RbacPrincipal(tenantId, principal.userAccountId());
            RbacScope scope = ownScope(canceller);
            permissionEvaluatorService.authorize(canceller, "APPROVAL_DELEGATE", scope, RbacTarget.NONE);
            // A-05: the delegation must belong to someone inside the canceller's own scope; a
            // delegation of another institution or department looks missing.
            if (scope.scopeType() != ScopeType.TENANT) {
                boolean delegatorInScope = permissionEvaluatorService
                        .heldAssignments(new RbacPrincipal(tenantId, delegation.getDelegatorUserId())).stream()
                        .anyMatch(a -> scope.scopeType() == ScopeType.INSTITUTION
                                ? Objects.equals(a.getInstitutionId(), scope.institutionId())
                                : Objects.equals(a.getDepartmentId(), scope.departmentId()));
                if (!delegatorInScope) {
                    throw new ResourceNotFoundException("Delegation not found: " + delegationId);
                }
            }
        }

        if ("CANCELLED".equals(delegation.getStatus())) {
            throw new BusinessRuleViolationException("DELEGATION_ALREADY_CANCELLED", "Delegation is already cancelled.");
        }

        Instant now = Instant.now();
        delegation.setStatus("CANCELLED");
        delegation.setCancelledAt(now);
        delegation.setCancelledBy(principal.userAccountId());
        delegation.setUpdatedAt(now);
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

    /**
     * Bulk approval per D5 bulkApprove (see class comment). Deliberately NOT one transaction: each
     * item is decided by the approvals engine in its own transaction so a failing item is reported
     * and the others stand (D5 BulkApproveResponse is per item).
     */
    public BulkApprovalResponse bulkDecide(UUID tenantId, TenantPrincipal principal, BulkApprovalRequest request) {
        RbacPrincipal caller = new RbacPrincipal(tenantId, principal.userAccountId());
        permissionEvaluatorService.authorize(caller, "APPROVAL_BULK", ownScope(caller), RbacTarget.NONE);

        if (request.approvalRequestIds() == null || request.approvalRequestIds().isEmpty()) {
            throw new BusinessRuleViolationException("EMPTY_BATCH", "At least one approval request ID is required for bulk decisions.");
        }
        // D5: rejects are never bulk.
        if (request.decision() == null || !"APPROVED".equalsIgnoreCase(request.decision().trim())) {
            throw new BusinessRuleViolationException("NOT_BULK_ELIGIBLE", "Only approvals can be decided in bulk; rejections never are (D5 bulkApprove).");
        }
        // Rule C12: Data Protection Officer approvals are never bulk-approved
        if ("DATA_PROTECTION".equalsIgnoreCase(request.processCategory())) {
            throw new BusinessRuleViolationException("DPO_BULK_APPROVAL_PROHIBITED",
                    "Data Protection Officer approvals (exports, deletions, retention) are never bulk-approved [Rule C12].");
        }
        String activeRole = TenantContext.isSet() ? TenantContext.currentActiveRoleCode() : null;
        if (activeRole == null) {
            throw new UnauthorizedException("No active role: choose an active role before a bulk approval.");
        }

        // Pre-pass: nothing is decided if any item is missing from this caller's scope (404) or is a DPO item (422).
        List<ApprovalRequest> items = new ArrayList<>();
        for (UUID requestId : request.approvalRequestIds()) {
            ApprovalRequest req = approvalRequestRepository
                    .findByIdTenantIdAndIdApprovalRequestId(tenantId, requestId)
                    .filter(r -> ApprovalInboxService.inCallerScope(r.getInstitutionId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Approval request not found: " + requestId));
            if ("DATA_PROTECTION".equalsIgnoreCase(req.getProcessCode()) || "DATA_PROTECTION".equalsIgnoreCase(req.getSubjectType())) {
                throw new BusinessRuleViolationException("DPO_BULK_APPROVAL_PROHIBITED",
                        "Data Protection Officer approvals (exports, deletions, retention) are never bulk-approved [Rule C12].");
            }
            items.add(req);
        }

        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        approvalBatchRepository.save(ApprovalBatch.builder()
                .id(new ApprovalBatchId(tenantId, batchId))
                .actedByUserId(principal.userAccountId())
                .actedActiveRole(activeRole)
                .batchComment(request.comment())
                .itemCount(items.size())
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .createdAt(now)
                .updatedAt(now)
                .rowVersion(1)
                .build());

        List<BulkApprovalResponse.ItemResult> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int succeeded = 0;
        for (ApprovalRequest req : items) {
            UUID requestId = req.getId().getApprovalRequestId();
            String outcome;
            if (ApprovalInboxService.resolveApprovalAction(req) == null) {
                outcome = "NOT_BULK_ELIGIBLE";
            } else {
                try {
                    approvalInboxService.decideApproval(tenantId, requestId,
                            new DecideApprovalRequestDto(req.getCurrentStepNo(), "APPROVED", request.comment(), req.getRowVersion()),
                            principal);
                    outcome = "APPROVED";
                    succeeded++;
                    // Rule AP-25: each item is logged separately
                    auditLogService.record(AuditEventType.APPROVAL_BULK_DECIDED, null, principal.userAccountId().toString(),
                            tenantId.toString(), "Bulk batch " + batchId + " decided request " + requestId + ": APPROVED");
                } catch (BusinessRuleViolationException e) {
                    boolean done = "REQUEST_NOT_PENDING".equals(e.getErrorCode()) || "STEP_ALREADY_DECIDED".equals(e.getErrorCode());
                    outcome = done ? "ALREADY_ACTIONED" : "NOT_CURRENT_APPROVER";
                    errors.add(requestId + ": " + e.getMessage());
                } catch (UnauthorizedException | ResourceNotFoundException e) {
                    outcome = "NOT_CURRENT_APPROVER";
                    errors.add(requestId + ": " + e.getMessage());
                }
            }
            results.add(new BulkApprovalResponse.ItemResult(requestId, outcome));
        }

        return new BulkApprovalResponse(batchId, items.size(), succeeded, items.size() - succeeded, errors, results);
    }

    /** The caller's own active scope (A-05); the tenant scope only if the evaluator gives none. */
    private RbacScope ownScope(RbacPrincipal rbac) {
        return Objects.requireNonNullElse(permissionEvaluatorService.ownScope(rbac), RbacScope.tenant());
    }
}
