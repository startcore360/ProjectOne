package com.edu360.api.finance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Verifies provider signatures against the exact raw request bytes. */
@Component
public class PaymentWebhookSignatureVerifier {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Duration STRIPE_TOLERANCE = Duration.ofMinutes(5);

    public boolean verifyRazorpay(byte[] rawBody, String signatureHeader, String secret) {
        if (!usable(rawBody, signatureHeader, secret)) return false;
        try {
            byte[] expected = hmac(secret, rawBody);
            byte[] supplied = HexFormat.of().parseHex(signatureHeader.trim());
            return MessageDigest.isEqual(expected, supplied);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean verifyStripe(byte[] rawBody, String signatureHeader, String secret, Instant now) {
        if (!usable(rawBody, signatureHeader, secret) || now == null) return false;
        String timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) continue;
            if ("t".equals(pair[0])) timestamp = pair[1];
            if ("v1".equals(pair[0])) signatures.add(pair[1]);
        }
        if (timestamp == null || signatures.isEmpty()) return false;

        final long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            return false;
        }
        final Instant signedAt;
        try {
            signedAt = Instant.ofEpochSecond(epochSeconds);
        } catch (RuntimeException ex) {
            return false;
        }
        if (Duration.between(signedAt, now).abs().compareTo(STRIPE_TOLERANCE) > 0) return false;

        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signedPayload = new byte[prefix.length + rawBody.length];
        System.arraycopy(prefix, 0, signedPayload, 0, prefix.length);
        System.arraycopy(rawBody, 0, signedPayload, prefix.length, rawBody.length);
        byte[] expected = hmac(secret, signedPayload);

        for (String candidate : signatures) {
            try {
                if (MessageDigest.isEqual(expected, HexFormat.of().parseHex(candidate))) return true;
            } catch (IllegalArgumentException ignored) {
                // A malformed v1 candidate does not invalidate another valid v1 candidate.
            }
        }
        return false;
    }

    private static boolean usable(byte[] rawBody, String signature, String secret) {
        return rawBody != null && rawBody.length > 0
                && signature != null && !signature.isBlank()
                && secret != null && !secret.isBlank();
    }

    private static byte[] hmac(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", ex);
        }
    }
}
