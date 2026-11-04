package com.edu360.api.institution;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.CalendarEventRequest;
import com.edu360.api.institution.dto.CalendarEventResponse;
import com.edu360.api.rbac.RbacPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: CALENDAR_LOCAL_SET - see CalendarEventService's javadoc. institutionId is a query param
 * on GET and a body field on POST, since the path only carries tenantId (CALENDAR_LOCAL_SET's
 * scope is "own institution", not "own tenant").
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/calendar-events")
@RequiredArgsConstructor
@Tag(name = "Institution setup", description = "Local calendar events (CALENDAR_LOCAL_SET)")
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    @PostMapping
    @Operation(summary = "Add a local holiday or event (CALENDAR_LOCAL_SET)")
    public ResponseEntity<CalendarEventResponse> addLocalEvent(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CalendarEventRequest request,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(calendarEventService.addLocalEvent(principal, request));
    }

    @GetMapping
    @Operation(summary = "List local calendar events for an institution (CALENDAR_LOCAL_SET)")
    public ResponseEntity<List<CalendarEventResponse>> listLocalEvents(
            @PathVariable UUID tenantId,
            @RequestParam UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(calendarEventService.listLocalEvents(principal, institutionId));
    }

    private RbacPrincipal tenantPrincipal(UUID tenantId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("CALENDAR_LOCAL_SET is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
