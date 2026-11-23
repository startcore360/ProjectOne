package com.edu360.api.admissions.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain hand-off for a payment provider after it has verified a gateway callback.
 * The payment module owns provider signatures, webhook delivery and reconciliation;
 * Admissions owns the application fee state transition and idempotency.
 */
public interface AdmissionPaymentEventHandler {
    void handleVerifiedApplicationFeeEvent(UUID tenantId, UUID applicationId, String eventId,
            String paymentReference, BigDecimal paidAmount);
}
