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
public class FeeStructureController {
    private final FeeStructureService service;

    @PostMapping({"/v1/tenants/{tenantId}/fee-structures", "/api/tenants/{tenantId}/fee-structures"})
    public Map<String, Object> create(@PathVariable UUID tenantId,
            @RequestBody FeeStructureService.FeeStructureRequest request, Authentication authentication) {
        return service.create(tenantId, principal(authentication, tenantId), request);
    }

    @PostMapping({"/v1/tenants/{tenantId}/fee-structures/{id}/submit",
                  "/api/tenants/{tenantId}/fee-structures/{id}/submit"})
    public Map<String, Object> submit(@PathVariable UUID tenantId, @PathVariable("id") UUID id,
            Authentication authentication) {
        return service.submit(tenantId, principal(authentication, tenantId), id);
    }

    private TenantPrincipal principal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)
                || !tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("Authenticated tenant principal required.");
        }
        return principal;
    }
}
