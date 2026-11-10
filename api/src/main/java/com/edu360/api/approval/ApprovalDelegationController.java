package com.edu360.api.approval;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.approval.dto.BulkApprovalRequest;
import com.edu360.api.approval.dto.BulkApprovalResponse;
import com.edu360.api.approval.dto.CreateDelegationRequest;
import com.edu360.api.approval.dto.DelegationResponse;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Approval Delegation & Bulk Operations", description = "Endpoints for approval delegation and bulk decisions [APPROVAL_DELEGATE, APPROVAL_BULK]")
public class ApprovalDelegationController {

    private final ApprovalDelegationService delegationService;

    @GetMapping({"/v1/tenants/{tenantId}/delegations", "/api/tenants/{tenantId}/delegations"})
    @Operation(summary = "List approval delegations for the current authenticated user")
    public ResponseEntity<List<DelegationResponse>> listDelegations(
            @PathVariable UUID tenantId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        List<DelegationResponse> responses = delegationService.listDelegations(tenantId, principal.userAccountId());
        return ResponseEntity.ok(responses);
    }

    @PostMapping({"/v1/tenants/{tenantId}/delegations", "/api/tenants/{tenantId}/delegations"})
    @Operation(summary = "Create an approval delegation [APPROVAL_DELEGATE / Action #19]")
    public ResponseEntity<DelegationResponse> createDelegation(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateDelegationRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        DelegationResponse response = delegationService.createDelegation(tenantId, principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping({"/v1/tenants/{tenantId}/delegations/{id}/cancellation", "/api/tenants/{tenantId}/delegations/{id}/cancellation"})
    @Operation(summary = "Cancel an active approval delegation")
    public ResponseEntity<DelegationResponse> cancelDelegation(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        DelegationResponse response = delegationService.cancelDelegation(tenantId, id, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/v1/tenants/{tenantId}/approvals/bulk-decisions", "/api/tenants/{tenantId}/approvals/bulk-decisions"})
    @Operation(summary = "Bulk approve or reject multiple requests [APPROVAL_BULK / Action #20]")
    public ResponseEntity<BulkApprovalResponse> bulkDecide(
            @PathVariable UUID tenantId,
            @Valid @RequestBody BulkApprovalRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        BulkApprovalResponse response = delegationService.bulkDecide(tenantId, principal, request);
        return ResponseEntity.ok(response);
    }

    private TenantPrincipal extractPrincipal(Authentication authentication, UUID pathTenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Authenticated tenant principal required.");
        }
        if (!principal.tenantId().equals(pathTenantId)) {
            throw new UnauthorizedException("Tenant mismatch between path and authentication.");
        }
        return principal;
    }
}
