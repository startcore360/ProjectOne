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
public class FeePaymentController {
    private final OnlinePaymentCheckoutService checkoutService;

    @PostMapping({"/v1/tenants/{tenantId}/payments/checkout", "/api/tenants/{tenantId}/payments/checkout"})
    public Map<String, Object> checkout(@PathVariable UUID tenantId,
            @RequestBody OnlinePaymentCheckoutService.CheckoutRequest request,
            Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)
                || !tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("Authenticated tenant principal required.");
        }
        return checkoutService.start(tenantId, principal, request);
    }
}
