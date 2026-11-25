package com.edu360.api.t10;

import com.edu360.api.config.TenantPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/v1/tenants/{tenantId}/communications", "/api/tenants/{tenantId}/communications"})
public class CommunicationDispatchController {
    private final T10DomainService service;
    @PostMapping("/dispatch") public Map<String,Object> dispatch(@PathVariable UUID tenantId,@RequestBody DispatchRequest request,Authentication auth) {
        return service.dispatch(tenantId,principal(auth,tenantId),request);
    }
    @GetMapping("/dispatch") public List<Map<String,Object>> history(@PathVariable UUID tenantId,@RequestParam UUID institutionId,Authentication auth) {
        return service.dispatchHistory(tenantId,principal(auth,tenantId),institutionId);
    }
    private TenantPrincipal principal(Authentication a,UUID t) {
        if(a==null || !(a.getPrincipal() instanceof TenantPrincipal p)) throw new AccessDeniedException("Tenant authentication required");
        if(!t.equals(p.tenantId())) throw new AccessDeniedException("Cross-tenant access forbidden");
        return p;
    }
    public record DispatchRequest(UUID institutionId,UUID recipientUserId,String phone,String email,String subject,String body,List<String> channels,boolean urgent) {}
}
