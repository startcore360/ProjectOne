package com.edu360.api.academic;

import com.edu360.api.config.TenantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Subject Allocation", description = "Allocating subjects to teachers [SUBJECT_ALLOCATE / Action #36]")
public class SubjectAllocationController {

    private final SubjectAllocationService subjectAllocationService;

    @GetMapping({"/v1/tenants/{tenantId}/subject-allocations", "/api/tenants/{tenantId}/subject-allocations"})
    @Operation(summary = "List subject allocations")
    public ResponseEntity<List<SubjectAllocationResponse>> listAllocations(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID institutionId,
            Authentication authentication
    ) {
        extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(subjectAllocationService.listAllocations(tenantId, institutionId));
    }

    @PostMapping({"/v1/tenants/{tenantId}/subject-allocations", "/api/tenants/{tenantId}/subject-allocations"})
    @Operation(summary = "Allocate a subject to a teacher [SUBJECT_ALLOCATE / Action #36]")
    public ResponseEntity<SubjectAllocationResponse> allocateSubject(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateSubjectAllocationRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectAllocationService.allocateSubject(tenantId, principal, request));
    }

    @PostMapping({"/v1/tenants/{tenantId}/subject-allocations/{id}/end", "/api/tenants/{tenantId}/subject-allocations/{id}/end"})
    @Operation(summary = "End a subject allocation")
    public ResponseEntity<SubjectAllocationResponse> endAllocation(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(subjectAllocationService.endAllocation(tenantId, principal, id));
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
