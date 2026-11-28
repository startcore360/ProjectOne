package com.edu360.api.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PaymentWebhookSignatureVerifierTest {
    private static final String TEST_SECRET = "test-only-not-a-real-gateway-secret";
    private final PaymentWebhookSignatureVerifier verifier = new PaymentWebhookSignatureVerifier();

    @Test
    void verifiesRazorpaySignatureAgainstExactRawBody() throws Exception {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String signature = hex(hmac(TEST_SECRET, body));

        assertThat(verifier.verifyRazorpay(body, signature, TEST_SECRET)).isTrue();
        assertThat(verifier.verifyRazorpay(body, signature, "different-test-secret")).isFalse();
        assertThat(verifier.verifyRazorpay((new String(body) + " ").getBytes(StandardCharsets.UTF_8),
                signature, TEST_SECRET)).isFalse();
    }

    @Test
    void verifiesStripeV1SignatureAndRejectsStaleTimestamp() throws Exception {
        Instant now = Instant.parse("2026-11-25T18:30:00Z");
        long timestamp = now.getEpochSecond();
        byte[] body = "{\"type\":\"payment_intent.succeeded\"}".getBytes(StandardCharsets.UTF_8);
        byte[] signed = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[signed.length + body.length];
        System.arraycopy(signed, 0, payload, 0, signed.length);
        System.arraycopy(body, 0, payload, signed.length, body.length);
        String signature = "t=" + timestamp + ",v1=" + hex(hmac(TEST_SECRET, payload));

        assertThat(verifier.verifyStripe(body, signature, TEST_SECRET, now)).isTrue();
        assertThat(verifier.verifyStripe(body, signature, TEST_SECRET, now.plusSeconds(301))).isFalse();
        assertThat(verifier.verifyStripe(body, signature, "different-test-secret", now)).isFalse();
    }

    @Test
    void acceptsAnyValidStripeV1SignatureAndRejectsMalformedHeaders() throws Exception {
        Instant now = Instant.parse("2026-11-25T18:30:00Z");
        long timestamp = now.getEpochSecond();
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(body, 0, payload, prefix.length, body.length);
        String signature = "t=" + timestamp + ",v1=not-hex,v1=" + hex(hmac(TEST_SECRET, payload));

        assertThat(verifier.verifyStripe(body, signature, TEST_SECRET, now)).isTrue();
        assertThat(verifier.verifyStripe(body, "t=bad,v1=abcd", TEST_SECRET, now)).isFalse();
        assertThat(verifier.verifyStripe(body, signature, "", now)).isFalse();
    }

    private static byte[] hmac(String secret, byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(payload);
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
