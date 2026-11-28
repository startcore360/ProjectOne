package com.edu360.keycloak.otp;

import java.nio.charset.StandardCharsets;

import org.keycloak.models.OTPPolicy;
import org.keycloak.models.utils.TimeBasedOTP;

/**
 * Checks a 6-digit (per realm policy) authenticator code against a pending secret, using
 * Keycloak's own TimeBasedOTP and the realm's OTP policy (algorithm, digits, period, look-ahead)
 * - the same rules Keycloak applies at sign-in, so a code accepted here also works there.
 * Kept separate from the JAX-RS resource so it can be unit-tested without a server.
 *
 * Edu360OtpResource generates the secret via HmacOTP.generateSecret(20) (raw ASCII).
 * When presented to the user via QR code or manual entry, Keycloak's TotpUtils encodes those
 * bytes to Base32: Base32.encode(secret.getBytes()). The authenticator app decodes that Base32
 * string back to the original raw bytes: secret.getBytes(StandardCharsets.UTF_8).
 * Keycloak's OTPCredentialProvider also validates using secret.getBytes(StandardCharsets.UTF_8).
 * Therefore, validation must use secret.getBytes(StandardCharsets.UTF_8).
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
