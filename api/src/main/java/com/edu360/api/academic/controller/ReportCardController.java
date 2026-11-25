package com.edu360.api.academic.controller;

import com.edu360.api.academic.ReportCardService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/academic/report-cards", "/v1/tenants/{tenantId}/academic/report-cards"})
public class ReportCardController {
    private final ReportCardService service;
    public ReportCardController(ReportCardService service) { this.service = service; }

    @GetMapping("/{reportCardId}")
    public Map<String, Object> get(@PathVariable UUID tenantId, @PathVariable UUID reportCardId, Authentication auth) {
        return service.get(tenantId, principal(auth, tenantId), reportCardId);
    }

    @PostMapping("/{reportCardId}/publish")
    public Map<String, Object> publish(@PathVariable UUID tenantId, @PathVariable UUID reportCardId, Authentication auth) {
        return service.publish(tenantId, principal(auth, tenantId), reportCardId);
    }

    private static TenantPrincipal principal(Authentication auth, UUID tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) throw new UnauthorizedException("Tenant authentication is required.");
        if (!tenantId.equals(p.tenantId())) throw new ResourceNotFoundException("Report card not found.");
        return p;
    }
}
