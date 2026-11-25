package com.edu360.api.t10;

import com.edu360.api.config.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/v1/tenants/{tenantId}/transport", "/api/tenants/{tenantId}/transport"})
public class TransportController {
    private final T10DomainService service;
    @GetMapping("/routes") public List<Map<String,Object>> routes(@PathVariable UUID tenantId,@RequestParam UUID institutionId,Authentication auth) {
        return service.transportRoutes(tenantId,principal(auth,tenantId),institutionId);
    }
    @PostMapping("/routes") public Map<String,Object> createRoute(@PathVariable UUID tenantId,@RequestBody RouteRequest r,Authentication auth) {
        return service.createRoute(tenantId,principal(auth,tenantId),r);
    }
    @PostMapping("/allocations") public Map<String,Object> allocate(@PathVariable UUID tenantId,@RequestBody AllocationRequest r,Authentication auth) {
        return service.allocateTransport(tenantId,principal(auth,tenantId),r);
    }
    private TenantPrincipal principal(Authentication a,UUID t) {
        if(a==null || !(a.getPrincipal() instanceof TenantPrincipal p)) throw new AccessDeniedException("Tenant authentication required");
        if(!t.equals(p.tenantId())) throw new AccessDeniedException("Cross-tenant access forbidden");
        return p;
    }
    public record StopRequest(String name,LocalTime pickupTime,LocalTime dropTime,BigDecimal feeAmount,int order) {}
    public record RouteRequest(UUID institutionId,String routeName,String vehicleNumber,String driverName,String driverPhone,int capacity,String transportFeeCode,List<StopRequest> stops) {}
    public record AllocationRequest(UUID institutionId,UUID routeId,UUID stopId,UUID passengerUserId) {}
}
