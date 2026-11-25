package com.edu360.api.finance;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FeeAssignmentController {
    private final FeeAssignmentService service;

    @PostMapping({"/v1/tenants/{tenantId}/fee-structures/{id}/assignments",
                  "/api/tenants/{tenantId}/fee-structures/{id}/assignments"})
    public Map<String, Object> assign(@PathVariable UUID tenantId, @PathVariable("id") UUID structureId,
            @RequestBody FeeAssignmentService.FeeAssignmentRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)
                || !tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("Authenticated tenant principal required.");
        }
        return service.assign(tenantId, principal, structureId, request);
    }
}
