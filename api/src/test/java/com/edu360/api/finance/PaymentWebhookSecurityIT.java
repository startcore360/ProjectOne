package com.edu360.api.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Security regression tests for the raw-body webhook signature boundary. */
class PaymentWebhookSecurityIT {
    private static final String SECRET = "test-only-payment-webhook-secret";
    private final PaymentWebhookSignatureVerifier verifier = new PaymentWebhookSignatureVerifier();

    @Test
    void razorpaySignatureMustMatchExactRawRequestBytes() throws Exception {
        byte[] raw = "{\"event\":\"payment.captured\",\"id\":1}".getBytes(StandardCharsets.UTF_8);
        String signature = hmac(SECRET, raw);

        assertThat(verifier.verifyRazorpay(raw, signature, SECRET)).isTrue();
        assertThat(verifier.verifyRazorpay((new String(raw, StandardCharsets.UTF_8) + " ")
                .getBytes(StandardCharsets.UTF_8), signature, SECRET)).isFalse();
        assertThat(verifier.verifyRazorpay(raw, signature, "wrong-secret")).isFalse();
        assertThat(verifier.verifyRazorpay(raw, "not-a-signature", SECRET)).isFalse();
    }

    @Test
    void stripeSignatureMustBeFreshAndCryptographicallyValid() throws Exception {
        var now = java.time.Instant.parse("2026-11-26T00:00:00Z");
        long timestamp = now.getEpochSecond();
        byte[] body = "{\"type\":\"checkout.session.completed\"}".getBytes(StandardCharsets.UTF_8);
        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signed = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, signed, 0, prefix.length);
        System.arraycopy(body, 0, signed, prefix.length, body.length);
        String header = "t=" + timestamp + ",v1=" + hmac(SECRET, signed);

        assertThat(verifier.verifyStripe(body, header, SECRET, now)).isTrue();
        assertThat(verifier.verifyStripe(body, header, SECRET, now.plusSeconds(301))).isFalse();
        assertThat(verifier.verifyStripe(body, header, "wrong-secret", now)).isFalse();
    }

    private static String hmac(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
