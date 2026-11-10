package com.edu360.keycloak.otp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.keycloak.models.OTPPolicy;
import org.keycloak.models.utils.HmacOTP;
import org.keycloak.models.utils.TimeBasedOTP;

class TotpCheckTest {

    private final OTPPolicy policy = OTPPolicy.DEFAULT_POLICY;

    private String currentCode(String secret) {
        return new TimeBasedOTP(policy.getAlgorithm(), policy.getDigits(), policy.getPeriod(),
                policy.getLookAheadWindow()).generateTOTP(secret);
    }

    @Test
    void acceptsTheCurrentCodeForTheSecret() {
        String secret = HmacOTP.generateSecret(20);
        assertTrue(TotpCheck.isValid(currentCode(secret), secret, policy));
        assertTrue(TotpCheck.isValid(" " + currentCode(secret) + " ", secret, policy));
    }

    @Test
    void rejectsACodeForAnotherSecret() {
        String secret = HmacOTP.generateSecret(20);
        String other = HmacOTP.generateSecret(20);
        assertFalse(TotpCheck.isValid(currentCode(other), secret, policy)
                && !currentCode(other).equals(currentCode(secret)));
    }

    @Test
    void rejectsMalformedInput() {
        String secret = HmacOTP.generateSecret(20);
        assertFalse(TotpCheck.isValid(null, secret, policy));
        assertFalse(TotpCheck.isValid("12345", secret, policy));
        assertFalse(TotpCheck.isValid("12a456", secret, policy));
        assertFalse(TotpCheck.isValid("1234567", secret, policy));
        assertFalse(TotpCheck.isValid("123456", null, policy));
    }
}
