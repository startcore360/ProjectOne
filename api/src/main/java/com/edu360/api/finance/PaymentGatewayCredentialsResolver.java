package com.edu360.api.finance;

import java.util.UUID;

/** Resolves institution-scoped payment credentials from the tenant's secret vault. */
public interface PaymentGatewayCredentialsResolver {
    PaymentGatewayCredentials resolve(UUID tenantId, UUID institutionId);

    record PaymentGatewayCredentials(
            String razorpayKeyId,
            String razorpayKeySecret,
            String razorpayWebhookSecret,
            String stripeSecretKey,
            String stripeWebhookSecret) {
        public boolean hasRazorpay() {
            return present(razorpayKeyId) && present(razorpayKeySecret) && present(razorpayWebhookSecret);
        }

        public boolean hasStripe() {
            return present(stripeSecretKey) && present(stripeWebhookSecret);
        }

        private static boolean present(String value) {
            return value != null && !value.isBlank();
        }
    }
}
