package com.edu360.api.attendance.staffcheckin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.attendance.staffcheckin.dto.CheckInInboxItemResponse;
import com.edu360.api.attendance.staffcheckin.dto.CheckInReviewDecisionRequest;
import com.edu360.api.attendance.staffcheckin.dto.CheckInStatusResponse;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Reviewer-facing endpoints for STAFF_ATTENDANCE_CHECKIN_REVIEW. Decisions go through the shared
 * approval engine (ApprovalInboxService.decideApproval), so the generic
 * /approval-requests/{id}/decisions endpoint and these two always agree.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Staff Attendance Check-In Review", description = "STAFF_ATTENDANCE_CHECKIN_REVIEW - approve/reject provisional self check-ins")
public class StaffCheckInReviewController {

    private final StaffCheckInService service;

    @GetMapping({"/v1/tenants/{tenantId}/attendance-checkins/pending", "/api/tenants/{tenantId}/attendance-checkins/pending"})
    @Operation(summary = "List provisional / check-in-only records waiting on the current reviewer")
    public ResponseEntity<List<CheckInInboxItemResponse>> pending(
            @PathVariable UUID tenantId,
            Authentication authentication) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(service.pendingForApprover(tenantId, principal));
    }

    @PostMapping({"/v1/tenants/{tenantId}/attendance-checkins/{checkinId}/approve",
            "/api/tenants/{tenantId}/attendance-checkins/{checkinId}/approve"})
    @Operation(summary = "Approve a provisional / check-in-only self check-in (remark optional)")
    public ResponseEntity<CheckInStatusResponse> approve(
            @PathVariable UUID tenantId,
            @PathVariable UUID checkinId,
            @RequestBody(required = false) CheckInReviewDecisionRequest request,
            Authentication authentication) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(service.approve(tenantId, principal, checkinId,
                request == null ? null : request.reason()));
    }

    @PostMapping({"/v1/tenants/{tenantId}/attendance-checkins/{checkinId}/reject",
            "/api/tenants/{tenantId}/attendance-checkins/{checkinId}/reject"})
    @Operation(summary = "Reject a provisional / check-in-only self check-in (reason mandatory)")
    public ResponseEntity<CheckInStatusResponse> reject(
            @PathVariable UUID tenantId,
            @PathVariable UUID checkinId,
            @RequestBody(required = false) CheckInReviewDecisionRequest request,
            Authentication authentication) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(service.reject(tenantId, principal, checkinId,
                request == null ? null : request.reason()));
    }

    private TenantPrincipal extractPrincipal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal p)) {
            throw new UnauthorizedException("Authentication required.");
        }
        if (!tenantId.equals(p.tenantId())) {
            throw new UnauthorizedException("Tenant ID in path does not match authenticated user's tenant.");
        }
        return p;
    }
}
