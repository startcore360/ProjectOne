package com.edu360.api.institution;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.GoLiveCheckResponse;
import com.edu360.api.institution.dto.LeaveApproverSetRequest;
import com.edu360.api.institution.dto.LeaveApproverSettingResponse;
import com.edu360.api.rbac.PermissionDecision;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Leave Approver Settings", description = "PRINCIPAL_LEAVE_APPROVER_SET & VC_LEAVE_APPROVER_SET")
public class LeaveApproverController {

    private final LeaveApproverService leaveApproverService;
    private final PermissionEvaluatorService permissionEvaluatorService;

    @PutMapping({
            "/v1/tenants/{tenantId}/institutions/{institutionId}/dpc-leave-approver",
            "/api/tenants/{tenantId}/institutions/{institutionId}/dpc-leave-approver",
            "/v1/tenants/{tenantId}/institutions/{institutionId}/principal-leave-approver",
            "/api/tenants/{tenantId}/institutions/{institutionId}/principal-leave-approver"
    })
    @Operation(summary = "Set who approves the Principal's own leave (PRINCIPAL_LEAVE_APPROVER_SET)")
    public ResponseEntity<LeaveApproverSettingResponse> setPrincipalLeaveApprover(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @Valid @RequestBody LeaveApproverSetRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);

        // Scope can be either institution-level (College/School Admin) or tenant-level (University Admin)
        PermissionDecision instDecision = permissionEvaluatorService.can(
                principal, "PRINCIPAL_LEAVE_APPROVER_SET", RbacScope.institution(institutionId), RbacTarget.NONE);
        boolean allowed = instDecision != null && instDecision.allowed();
        if (!allowed) {
            PermissionDecision tenantDecision = permissionEvaluatorService.can(
                    principal, "PRINCIPAL_LEAVE_APPROVER_SET", RbacScope.tenant(), RbacTarget.NONE);
            allowed = tenantDecision != null && tenantDecision.allowed();
        }

        if (!allowed) {
            throw new UnauthorizedException("PRINCIPAL_LEAVE_APPROVER_SET not granted for this scope");
        }

        LeaveApproverSettingResponse response = leaveApproverService.setPrincipalLeaveApprover(
                tenantId, institutionId, request, principal.userAccountId(), httpRequest.getRemoteAddr());

        return ResponseEntity.ok()
                .eTag("\"" + response.rowVersion() + "\"")
                .body(response);
    }

    @GetMapping({
            "/v1/tenants/{tenantId}/institutions/{institutionId}/dpc-leave-approver",
            "/api/tenants/{tenantId}/institutions/{institutionId}/dpc-leave-approver",
            "/v1/tenants/{tenantId}/institutions/{institutionId}/principal-leave-approver",
            "/api/tenants/{tenantId}/institutions/{institutionId}/principal-leave-approver"
    })
    @Operation(summary = "Get active Principal leave approver")
    public ResponseEntity<LeaveApproverSettingResponse> getPrincipalLeaveApprover(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {

        tenantPrincipal(tenantId, authentication);
        return leaveApproverService.getPrincipalLeaveApprover(tenantId, institutionId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No active Principal leave approver set for institution: " + institutionId));
    }

    @PutMapping({
            "/v1/tenants/{tenantId}/dpc-leave-approver",
            "/api/tenants/{tenantId}/dpc-leave-approver",
            "/v1/tenants/{tenantId}/vc-leave-approver",
            "/api/tenants/{tenantId}/vc-leave-approver"
    })
    @Operation(summary = "Set who approves the Vice-Chancellor's own leave (VC_LEAVE_APPROVER_SET)")
    public ResponseEntity<LeaveApproverSettingResponse> setVcLeaveApprover(
            @PathVariable UUID tenantId,
            @Valid @RequestBody LeaveApproverSetRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);

        permissionEvaluatorService.authorize(
                principal, "VC_LEAVE_APPROVER_SET", RbacScope.tenant(), RbacTarget.NONE);

        LeaveApproverSettingResponse response = leaveApproverService.setVcLeaveApprover(
                tenantId, request, principal.userAccountId(), httpRequest.getRemoteAddr());

        return ResponseEntity.ok()
                .eTag("\"" + response.rowVersion() + "\"")
                .body(response);
    }

    @GetMapping({
            "/v1/tenants/{tenantId}/dpc-leave-approver",
            "/api/tenants/{tenantId}/dpc-leave-approver",
            "/v1/tenants/{tenantId}/vc-leave-approver",
            "/api/tenants/{tenantId}/vc-leave-approver"
    })
    @Operation(summary = "Get active Vice-Chancellor leave approver")
    public ResponseEntity<LeaveApproverSettingResponse> getVcLeaveApprover(
            @PathVariable UUID tenantId,
            Authentication authentication) {

        tenantPrincipal(tenantId, authentication);
        return leaveApproverService.getVcLeaveApprover(tenantId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No active Vice-Chancellor leave approver set for tenant: " + tenantId));
    }

    @GetMapping({
            "/v1/tenants/{tenantId}/go-live-check",
            "/api/tenants/{tenantId}/go-live-check"
    })
    @Operation(summary = "Check missing go-live approver requirements (Rule C7)")
    public ResponseEntity<GoLiveCheckResponse> getGoLiveCheck(
            @PathVariable UUID tenantId,
            Authentication authentication) {

        tenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(leaveApproverService.getGoLiveCheck(tenantId));
    }

    private RbacPrincipal tenantPrincipal(UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Authentication as tenant user is required");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in URL does not match authenticated tenant");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
