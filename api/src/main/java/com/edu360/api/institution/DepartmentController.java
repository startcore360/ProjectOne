package com.edu360.api.institution;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.HodAssignRequest;
import com.edu360.api.institution.dto.HodAssignResponse;
import com.edu360.api.rbac.RbacPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: HOD_ASSIGN - see HodAssignmentService's javadoc for the full rule. Deliberately under
 * /api, not /v1, for the same reason UserCreationController gives: every existing route in this
 * codebase already lives under /api.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/departments/{departmentId}")
@RequiredArgsConstructor
@Tag(name = "Institution setup", description = "Department-level institution setup (HOD_ASSIGN)")
public class DepartmentController {

    private final HodAssignmentService hodAssignmentService;

    @PutMapping("/hod")
    @Operation(summary = "Make a faculty member HOD of this department (HOD_ASSIGN)")
    public ResponseEntity<HodAssignResponse> assignHod(
            @PathVariable UUID tenantId,
            @PathVariable UUID departmentId,
            @Valid @RequestBody HodAssignRequest request,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(hodAssignmentService.assignHod(principal, departmentId, request));
    }

    private RbacPrincipal tenantPrincipal(UUID tenantId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("HOD_ASSIGN is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
