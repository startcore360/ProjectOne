package com.edu360.api.selfservice;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.selfservice.dto.LeaveBalanceDto;
import com.edu360.api.selfservice.dto.LeaveRequestDto;
import com.edu360.api.selfservice.dto.NoticeDto;
import com.edu360.api.selfservice.dto.NotificationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Self-Service", description = "Endpoints for employee self-service [SELF_SERVICE_VIEW / Action #7]")
public class SelfServiceController {

    private final SelfServiceService selfService;

    @GetMapping({"/v1/tenants/{tenantId}/me/notifications", "/api/tenants/{tenantId}/me/notifications"})
    @Operation(summary = "Get current user notifications")
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @PathVariable UUID tenantId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(selfService.getNotifications(tenantId, principal.userAccountId()));
    }

    @GetMapping({"/v1/tenants/{tenantId}/me/leave-balances", "/api/tenants/{tenantId}/me/leave-balances"})
    @Operation(summary = "Get current user leave balances")
    public ResponseEntity<List<LeaveBalanceDto>> getLeaveBalances(
            @PathVariable UUID tenantId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(selfService.getLeaveBalances(tenantId, principal.userAccountId()));
    }

    @GetMapping({"/v1/tenants/{tenantId}/me/leave-requests", "/api/tenants/{tenantId}/me/leave-requests"})
    @Operation(summary = "Get current user leave requests")
    public ResponseEntity<List<LeaveRequestDto>> getLeaveRequests(
            @PathVariable UUID tenantId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(selfService.getLeaveRequests(tenantId, principal.userAccountId()));
    }

    @GetMapping({"/v1/tenants/{tenantId}/notices", "/api/tenants/{tenantId}/notices"})
    @Operation(summary = "Get published tenant notices")
    public ResponseEntity<List<NoticeDto>> getNotices(
            @PathVariable UUID tenantId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(selfService.getNotices(tenantId, principal.userAccountId()));
    }

    private TenantPrincipal extractPrincipal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal p)) {
            throw new AccessDeniedException("Authentication required");
        }
        if (!tenantId.equals(p.tenantId())) {
            throw new AccessDeniedException("Cross-tenant access forbidden");
        }
        return p;
    }
}
