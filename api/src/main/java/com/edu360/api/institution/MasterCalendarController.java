package com.edu360.api.institution;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.AcademicSessionResponse;
import com.edu360.api.institution.dto.CreateAcademicSessionRequest;
import com.edu360.api.institution.dto.CreateMasterCalendarEventRequest;
import com.edu360.api.institution.dto.MasterCalendarEventResponse;
import com.edu360.api.rbac.RbacPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST Controller for CALENDAR_MASTER_SET (Action Code #28) - Academic Sessions and Master Calendar Events.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}")
@RequiredArgsConstructor
@Tag(name = "Academic Calendar", description = "Academic Sessions and Master Calendar Events (CALENDAR_MASTER_SET)")
public class MasterCalendarController {

    private final MasterCalendarService masterCalendarService;

    @PostMapping("/academic-sessions")
    @Operation(summary = "Create an academic session (CALENDAR_MASTER_SET)")
    public ResponseEntity<AcademicSessionResponse> createAcademicSession(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateAcademicSessionRequest request,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        AcademicSessionResponse response = masterCalendarService.createAcademicSession(principal, request);
        return ResponseEntity
                .created(URI.create("/api/tenants/" + tenantId + "/academic-sessions/" + response.academicSessionId()))
                .body(response);
    }

    @GetMapping("/academic-sessions")
    @Operation(summary = "List academic sessions (CALENDAR_MASTER_SET)")
    public ResponseEntity<List<AcademicSessionResponse>> listAcademicSessions(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(masterCalendarService.listAcademicSessions(principal, institutionId));
    }

    @GetMapping("/academic-sessions/{sessionId}")
    @Operation(summary = "Get an academic session (CALENDAR_MASTER_SET)")
    public ResponseEntity<AcademicSessionResponse> getAcademicSession(
            @PathVariable UUID tenantId,
            @PathVariable UUID sessionId,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(masterCalendarService.getAcademicSession(principal, sessionId));
    }

    @org.springframework.web.bind.annotation.PutMapping("/academic-sessions/{sessionId}")
    @Operation(summary = "Update an academic session (CALENDAR_MASTER_SET)")
    public ResponseEntity<AcademicSessionResponse> updateAcademicSession(
            @PathVariable UUID tenantId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody com.edu360.api.institution.dto.UpdateAcademicSessionRequest request,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(masterCalendarService.updateAcademicSession(principal, sessionId, request));
    }

    @PostMapping("/master-calendar-events")
    @Operation(summary = "Create a master calendar event (CALENDAR_MASTER_SET)")
    public ResponseEntity<MasterCalendarEventResponse> createMasterCalendarEvent(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateMasterCalendarEventRequest request,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        MasterCalendarEventResponse response = masterCalendarService.createMasterCalendarEvent(principal, request);
        return ResponseEntity
                .created(URI.create("/api/tenants/" + tenantId + "/master-calendar-events/" + response.academicCalendarEventId()))
                .body(response);
    }

    @GetMapping("/master-calendar-events")
    @Operation(summary = "List master calendar events (CALENDAR_MASTER_SET)")
    public ResponseEntity<List<MasterCalendarEventResponse>> listMasterCalendarEvents(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID academicSessionId,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(masterCalendarService.listMasterCalendarEvents(principal, academicSessionId));
    }

    private RbacPrincipal tenantPrincipal(UUID tenantId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("CALENDAR_MASTER_SET is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
