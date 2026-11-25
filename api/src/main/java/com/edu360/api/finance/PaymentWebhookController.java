package com.edu360.api.finance;

import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentWebhookController {
    private final TenantRepository tenants;
    private final PaymentReconciliationService reconciliation;

    @PostMapping("/api/webhooks/payments/{tenantId}/razorpay")
    public ResponseEntity<Map<String, Object>> razorpay(@PathVariable UUID tenantId,
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        return process(tenantId, "RAZORPAY", rawBody, signature, eventId);
    }

    @PostMapping("/api/webhooks/payments/{tenantId}/stripe")
    public ResponseEntity<Map<String, Object>> stripe(@PathVariable UUID tenantId,
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        return process(tenantId, "STRIPE", rawBody, signature, null);
    }

    private ResponseEntity<Map<String, Object>> process(UUID tenantId, String provider,
            byte[] rawBody, String signature, String providerEventId) {
        Tenant tenant = tenants.findById(tenantId).orElse(null);
        if (tenant == null || (tenant.getState() != TenantState.ACTIVE && tenant.getState() != TenantState.TRIAL)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "TENANT_NOT_FOUND"));
        }
        TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef(),
                "ALL", Set.of(), "SYSTEM", ScopeType.TENANT);
        try {
            PaymentReconciliationService.WebhookResult result =
                    reconciliation.process(tenantId, provider, rawBody, signature, providerEventId);
            if ("REJECTED_SIGNATURE".equals(result.outcome())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("code", "INVALID_GATEWAY_SIGNATURE"));
            }
            return ResponseEntity.ok(Map.of(
                    "outcome", result.outcome(),
                    "paymentId", result.paymentId() == null ? "" : result.paymentId(),
                    "gatewayTransactionId", result.gatewayTransactionId() == null ? "" : result.gatewayTransactionId()));
        } finally {
            TenantContext.clear();
        }
    }
}
