package com.edu360.api.attendance.staffcheckin;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.attendance.staffcheckin.dto.LocationConfigRequest;
import com.edu360.api.attendance.staffcheckin.dto.LocationConfigResponse;
import com.edu360.api.config.TenantPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Institution/tenant-admin configuration of the self-check-in geofence (STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Staff Attendance Location Config", description = "STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE")
public class AttendanceLocationConfigController {

    private final AttendanceLocationConfigService service;

    @GetMapping({"/v1/tenants/{tenantId}/institutions/{institutionId}/attendance-checkin-location",
            "/api/tenants/{tenantId}/institutions/{institutionId}/attendance-checkin-location"})
    @Operation(summary = "Get this institution's self-check-in geofence configuration")
    public ResponseEntity<LocationConfigResponse> get(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(service.get(tenantId, principal.userAccountId(), institutionId));
    }

    @PutMapping({"/v1/tenants/{tenantId}/institutions/{institutionId}/attendance-checkin-location",
            "/api/tenants/{tenantId}/institutions/{institutionId}/attendance-checkin-location"})
    @Operation(summary = "Create or update this institution's self-check-in geofence configuration")
    public ResponseEntity<LocationConfigResponse> upsert(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @RequestBody LocationConfigRequest request,
            Authentication authentication) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(service.upsert(tenantId, principal.userAccountId(), institutionId, request));
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
