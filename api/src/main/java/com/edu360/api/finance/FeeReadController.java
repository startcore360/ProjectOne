package com.edu360.api.finance;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FeeReadController {
    private final FeeReadService service;

    @GetMapping({"/v1/tenants/{tenantId}/fee-heads", "/api/tenants/{tenantId}/fee-heads"})
    public List<Map<String, Object>> listFeeHeads(@PathVariable UUID tenantId,
            @RequestParam(required = false) UUID institutionId, Authentication authentication) {
        return service.listFeeHeads(tenantId, principal(authentication, tenantId), institutionId);
    }

    @GetMapping({"/v1/tenants/{tenantId}/fee-heads/{id}", "/api/tenants/{tenantId}/fee-heads/{id}"})
    public Map<String, Object> getFeeHead(@PathVariable UUID tenantId, @PathVariable("id") UUID id,
            @RequestParam(required = false) UUID institutionId, Authentication authentication) {
        return service.getFeeHead(tenantId, principal(authentication, tenantId), institutionId, id);
    }

    @GetMapping({"/v1/tenants/{tenantId}/fee-structures", "/api/tenants/{tenantId}/fee-structures"})
    public List<Map<String, Object>> listFeeStructures(@PathVariable UUID tenantId,
            @RequestParam(required = false) UUID institutionId, Authentication authentication) {
        return service.listFeeStructures(tenantId, principal(authentication, tenantId), institutionId);
    }

    @GetMapping({"/v1/tenants/{tenantId}/student-fee-accounts/{id}/ledger",
                 "/api/tenants/{tenantId}/student-fee-accounts/{id}/ledger"})
    public Map<String, Object> getStudentLedger(@PathVariable UUID tenantId, @PathVariable("id") UUID id,
            Authentication authentication) {
        return service.getStudentLedger(tenantId, principal(authentication, tenantId), id);
    }

    @GetMapping({"/v1/tenants/{tenantId}/student-fee-accounts/{id}/invoices",
                 "/api/tenants/{tenantId}/student-fee-accounts/{id}/invoices"})
    public List<Map<String, Object>> listInvoices(@PathVariable UUID tenantId, @PathVariable("id") UUID id,
            Authentication authentication) {
        return service.listInvoices(tenantId, principal(authentication, tenantId), id);
    }

    private TenantPrincipal principal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)
                || !tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("Authenticated tenant principal required.");
        }
        return principal;
    }
}
