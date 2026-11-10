package com.edu360.keycloak.otp;

import java.nio.charset.StandardCharsets;

import org.keycloak.models.OTPPolicy;
import org.keycloak.models.utils.TimeBasedOTP;

/**
 * Checks a 6-digit (per realm policy) authenticator code against a pending secret, using
 * Keycloak's own TimeBasedOTP and the realm's OTP policy (algorithm, digits, period, look-ahead)
 * - the same rules Keycloak applies at sign-in, so a code accepted here also works there.
 * Kept separate from the JAX-RS resource so it can be unit-tested without a server.
 */
final class TotpCheck {

    private TotpCheck() {
    }

    static boolean isValid(String code, String secret, OTPPolicy policy) {
        if (code == null || secret == null || policy == null) {
            return false;
        }
        String trimmed = code.trim();
        if (!trimmed.matches("\\d{" + policy.getDigits() + "}")) {
            return false;
        }
        TimeBasedOTP totp = new TimeBasedOTP(policy.getAlgorithm(), policy.getDigits(), policy.getPeriod(),
                policy.getLookAheadWindow());
        return totp.validateTOTP(trimmed, secret.getBytes(StandardCharsets.UTF_8));
    }
}
