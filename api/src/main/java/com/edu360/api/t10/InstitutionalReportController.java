package com.edu360.api.t10;

import com.edu360.api.config.TenantPrincipal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/v1/tenants/{tenantId}/institutional-reports", "/api/tenants/{tenantId}/institutional-reports"})
public class InstitutionalReportController {
    private final ExecutiveAnalyticsService analytics;
    @GetMapping("/executive-dashboard") public Map<String,Object> dashboard(@PathVariable UUID tenantId,@RequestParam UUID institutionId,
            @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,Authentication auth) {
        return analytics.dashboard(tenantId,principal(auth,tenantId),institutionId,from,to);
    }
    private TenantPrincipal principal(Authentication a,UUID t) {
        if(a==null || !(a.getPrincipal() instanceof TenantPrincipal p)) throw new AccessDeniedException("Tenant authentication required");
        if(!t.equals(p.tenantId())) throw new AccessDeniedException("Cross-tenant access forbidden");
        return p;
    }
}
