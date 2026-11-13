package com.edu360.api.institution;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.RbacPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/staff", "/api/tenants/{tenantId}/staff"})
public class StaffDirectoryController {
    private final StaffDirectoryService service;
    private final StaffOnboardingService onboarding;

    public StaffDirectoryController(StaffDirectoryService service, StaffOnboardingService onboarding) {
        this.service = service;
        this.onboarding = onboarding;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable UUID tenantId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        RbacPrincipal actor = principal(authentication, tenantId);
        if (role == null || role.isBlank()) {
            return ResponseEntity.ok(service.list(actor, q, limit));
        }
        if (!"TEACHING".equalsIgnoreCase(role.trim())) {
            throw new com.edu360.api.exception.BusinessRuleViolationException(
                    "VALIDATION_FAILED", "Only role=TEACHING is supported by the staff picker.");
        }
        return ResponseEntity.ok(service.teacherLookup(actor, institutionId, departmentId, q, limit));
    }

    @PostMapping
    public ResponseEntity<StaffOnboardingResponse> create(@PathVariable UUID tenantId,
            @Valid @RequestBody StaffProfileRequest request, Authentication authentication) {
        StaffOnboardingResponse response = onboarding.onboard(principal(authentication, tenantId), request);
        return ResponseEntity.status(201).eTag(etag(response.staff().version())).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffProfileResponse> get(@PathVariable UUID tenantId,
            @PathVariable UUID id, Authentication authentication) {
        StaffProfileResponse response = service.get(principal(authentication, tenantId), id);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<StaffProfileResponse> update(@PathVariable UUID tenantId,
            @PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody StaffProfileUpdateRequest request, Authentication authentication) {
        StaffProfileResponse response = service.update(principal(authentication, tenantId), id, ifMatch, request);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @GetMapping("/{id}/assignments")
    public ResponseEntity<List<StaffAssignmentResponse>> listAssignments(@PathVariable UUID tenantId,
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(service.listAssignments(principal(authentication, tenantId), id));
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<StaffAssignmentResponse> addAssignment(@PathVariable UUID tenantId,
            @PathVariable UUID id, @Valid @RequestBody StaffAssignmentRequest request,
            Authentication authentication) {
        StaffAssignmentResponse response = service.addAssignment(principal(authentication, tenantId), id, request);
        return ResponseEntity.status(201).eTag(etag(response.version())).body(response);
    }

    @PatchMapping("/{id}/assignments/{assignmentId}")
    public ResponseEntity<StaffAssignmentResponse> endAssignment(@PathVariable UUID tenantId,
            @PathVariable UUID id, @PathVariable UUID assignmentId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody EndStaffAssignmentRequest request, Authentication authentication) {
        StaffAssignmentResponse response = service.endAssignment(principal(authentication, tenantId),
                id, assignmentId, ifMatch, request);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @PostMapping("/{id}/service-records")
    public ResponseEntity<StaffServiceRecordResponse> addServiceRecord(@PathVariable UUID tenantId,
            @PathVariable UUID id, @Valid @RequestBody StaffServiceRecordRequest request,
            Authentication authentication) {
        return ResponseEntity.status(201).body(service.addServiceRecord(principal(authentication, tenantId), id, request));
    }

    @GetMapping("/{id}/service-records")
    public ResponseEntity<List<StaffServiceRecordResponse>> listServiceRecords(@PathVariable UUID tenantId,
            @PathVariable UUID id, @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        return ResponseEntity.ok(service.listServiceRecords(principal(authentication, tenantId), id, limit));
    }

    private static RbacPrincipal principal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal p)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(p.tenantId())) throw new ResourceNotFoundException("Staff record not found.");
        return new RbacPrincipal(p.tenantId(), p.userAccountId());
    }

    private static String etag(Integer version) {
        return "\"" + (version == null ? 1 : version) + "\"";
    }
}
