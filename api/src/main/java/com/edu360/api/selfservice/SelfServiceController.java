package com.edu360.api.selfservice;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.selfservice.dto.LeaveBalanceDto;
import com.edu360.api.selfservice.dto.LeaveRequestDto;
import com.edu360.api.selfservice.dto.LeaveRequestCreateRequest;
import com.edu360.api.selfservice.dto.LeaveRequestResponse;
import com.edu360.api.selfservice.dto.StaffLeaveTypeResponse;
import com.edu360.api.selfservice.dto.NoticeDto;
import com.edu360.api.selfservice.dto.NotificationDto;
import com.edu360.api.selfservice.dto.NotificationSummaryDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
@Tag(name = "Self-Service", description = "Endpoints for employee self-service [SELF_SERVICE_VIEW / Action #7]")
public class SelfServiceController {

    private final SelfServiceService selfService;
    private StaffLeaveService staffLeaveService;

    @Autowired(required = false)
    public void setStaffLeaveService(StaffLeaveService staffLeaveService) {
        this.staffLeaveService = staffLeaveService;
    }

    @GetMapping({"/v1/tenants/{tenantId}/me/notifications", "/api/tenants/{tenantId}/me/notifications"})
    @Operation(summary = "Get current user notifications")
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @PathVariable UUID tenantId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(selfService.getNotifications(tenantId, principal.userAccountId(), limit));
    }

    /** Compatibility overload for direct controller callers; HTTP uses the limit-aware method. */
    public ResponseEntity<List<NotificationDto>> getNotifications(UUID tenantId, Authentication authentication) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(selfService.getNotifications(tenantId, principal.userAccountId()));
    }

    @GetMapping({"/v1/tenants/{tenantId}/me/notifications/summary", "/api/tenants/{tenantId}/me/notifications/summary"})
    @Operation(summary = "Get the current user's unread notification count")
    public ResponseEntity<NotificationSummaryDto> getNotificationSummary(
            @PathVariable UUID tenantId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(selfService.getNotificationSummary(tenantId, principal.userAccountId()));
    }

    @PostMapping({"/v1/tenants/{tenantId}/me/notifications/{notificationId}/read", "/api/tenants/{tenantId}/me/notifications/{notificationId}/read"})
    @Operation(summary = "Mark one of the current user notifications as read")
    public ResponseEntity<Void> markNotificationRead(
            @PathVariable UUID tenantId,
            @PathVariable UUID notificationId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        selfService.markNotificationRead(tenantId, principal.userAccountId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/v1/tenants/{tenantId}/me/notifications/read-all", "/api/tenants/{tenantId}/me/notifications/read-all"})
    @Operation(summary = "Mark all current user notifications as read")
    public ResponseEntity<Void> markAllNotificationsRead(
            @PathVariable UUID tenantId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        selfService.markAllNotificationsRead(tenantId, principal.userAccountId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/v1/tenants/{tenantId}/me/leave-types", "/api/tenants/{tenantId}/me/leave-types"})
    @Operation(summary = "List active leave types available to the current staff member")
    public ResponseEntity<List<StaffLeaveTypeResponse>> getStaffLeaveTypes(
            @PathVariable UUID tenantId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        if (principal.userType() != com.edu360.api.identity.UserType.STAFF || staffLeaveService == null) {
            throw new AccessDeniedException("Staff leave types are available to authenticated staff users only");
        }
        return ResponseEntity.ok(staffLeaveService.availableLeaveTypes(tenantId, principal.userAccountId()));
    }

    @GetMapping({"/v1/tenants/{tenantId}/me/leave-balances", "/api/tenants/{tenantId}/me/leave-balances"})
    @Operation(summary = "Get current user leave balances")
    public ResponseEntity<List<LeaveBalanceDto>> getLeaveBalances(
            @PathVariable UUID tenantId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        if (staffLeaveService != null && principal.userType() == com.edu360.api.identity.UserType.STAFF) {
            return ResponseEntity.ok(staffLeaveService.ownBalances(tenantId, principal.userAccountId()).stream()
                    .map(b -> new LeaveBalanceDto(b.leaveBalanceId(), b.leaveTypeId(), b.leaveYear(),
                            b.creditedDays(), b.usedDays(), b.availableDays())).toList());
        }
        return ResponseEntity.ok(selfService.getLeaveBalances(tenantId, principal.userAccountId()));
    }

    @PostMapping({"/v1/tenants/{tenantId}/me/leave-requests", "/api/tenants/{tenantId}/me/leave-requests"})
    @Operation(summary = "Submit a staff leave request into the configured approval chain")
    public ResponseEntity<LeaveRequestResponse> submitLeaveRequest(
            @PathVariable UUID tenantId,
            @RequestBody LeaveRequestCreateRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        if (principal.userType() != com.edu360.api.identity.UserType.STAFF || staffLeaveService == null) {
            throw new AccessDeniedException("Staff leave submission is available to authenticated staff users only");
        }
        return ResponseEntity.status(201).body(staffLeaveService.submit(tenantId, principal, request));
    }

    @PostMapping({"/v1/tenants/{tenantId}/me/leave-requests/{leaveRequestId}/cancel", "/api/tenants/{tenantId}/me/leave-requests/{leaveRequestId}/cancel"})
    @Operation(summary = "Cancel own leave request before its start date")
    public ResponseEntity<Void> cancelOwnLeaveRequest(
            @PathVariable UUID tenantId,
            @PathVariable UUID leaveRequestId,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        if (staffLeaveService == null) throw new IllegalStateException("Staff leave service is unavailable");
        staffLeaveService.cancelOwn(tenantId, principal.userAccountId(), leaveRequestId);
        return ResponseEntity.noContent().build();
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
