package com.edu360.api.approval;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.approval.dto.ApprovalInboxResponseDto;
import com.edu360.api.approval.dto.ApprovalSummaryDto;
import com.edu360.api.approval.dto.BulkApproveRequestDto;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.approval.dto.DecideApprovalResponseDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Tenant shared approvals inbox, request details, and decision workflow")
public class ApprovalInboxController {

    private final ApprovalInboxService approvalInboxService;

    @GetMapping({"/v1/tenants/{tenantId}/approvals/inbox", "/api/tenants/{tenantId}/approvals/inbox"})
    @Operation(summary = "List pending approval steps for the caller (APPROVALS_INBOX)")
    public ResponseEntity<ApprovalInboxResponseDto> listPendingApprovals(
            @PathVariable UUID tenantId,
            Authentication authentication) {

        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        ApprovalInboxResponseDto response = approvalInboxService.listPendingApprovals(tenantId, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/v1/tenants/{tenantId}/approval-requests/{id}", "/api/tenants/{tenantId}/approval-requests/{id}"})
    @Operation(summary = "Get approval request with steps (requester sees own, approver sees in-scope)")
    public ResponseEntity<ApprovalSummaryDto> getApprovalRequest(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication) {

        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        ApprovalSummaryDto response = approvalInboxService.getApprovalRequest(tenantId, id, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/v1/tenants/{tenantId}/approval-requests/{id}/decisions", "/api/tenants/{tenantId}/approval-requests/{id}/decisions"})
    @Operation(summary = "Approve, reject, or forward at the step the caller holds; never own request")
    public ResponseEntity<DecideApprovalResponseDto> decideApproval(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody DecideApprovalRequestDto request,
            Authentication authentication) {

        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        DecideApprovalResponseDto response = approvalInboxService.decideApproval(tenantId, id, request, principal);
        return ResponseEntity.ok(response);
    }


    private TenantPrincipal extractPrincipal(Authentication auth, UUID tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Authentication required.");
        }
        if (tenantId != null && !tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("Tenant ID in path does not match authenticated user's tenant.");
        }
        return principal;
    }
}
