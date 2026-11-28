package com.edu360.api.attendance.staffcheckin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.attendance.staffcheckin.dto.CheckInStatusResponse;
import com.edu360.api.attendance.staffcheckin.dto.CheckInSubmitRequest;
import com.edu360.api.config.TenantPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Staff-facing endpoints: submit a self-check-in, view own status/history. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Staff Attendance Check-In", description = "STAFF_ATTENDANCE_CHECKIN_SUBMIT - selfie/liveness/geofenced self check-in")
public class StaffCheckInController {

    private final StaffCheckInService service;

    @PostMapping({"/v1/tenants/{tenantId}/me/attendance-checkins", "/api/tenants/{tenantId}/me/attendance-checkins"})
    @Operation(summary = "Submit a selfie-and-location self-attendance check-in")
    public ResponseEntity<CheckInStatusResponse> submit(
            @PathVariable UUID tenantId,
            @RequestBody CheckInSubmitRequest request,
            Authentication authentication) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.status(201).body(service.submit(tenantId, principal, request));
    }

    @GetMapping({"/v1/tenants/{tenantId}/me/attendance-checkins", "/api/tenants/{tenantId}/me/attendance-checkins"})
    @Operation(summary = "List the current user's own check-in history")
    public ResponseEntity<List<CheckInStatusResponse>> ownHistory(
            @PathVariable UUID tenantId,
            Authentication authentication) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(service.ownHistory(tenantId, principal.userAccountId()));
    }

    @GetMapping({"/v1/tenants/{tenantId}/me/attendance-checkins/{checkinId}",
            "/api/tenants/{tenantId}/me/attendance-checkins/{checkinId}"})
    @Operation(summary = "View the status of one of the current user's own check-ins")
    public ResponseEntity<CheckInStatusResponse> ownStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID checkinId,
            Authentication authentication) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(service.ownStatus(tenantId, principal.userAccountId(), checkinId));
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
