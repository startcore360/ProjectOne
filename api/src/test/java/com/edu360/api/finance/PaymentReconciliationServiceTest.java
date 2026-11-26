package com.edu360.api.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Ensures unauthenticated webhook deliveries cannot reserve a legitimate provider event ID. */
@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {
    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private PaymentWebhookSignatureVerifier verifier;
    @Mock private PaymentGatewayCredentialsResolver credentialsResolver;

    @Test
    void invalidSignatureDoesNotPersistOrReserveProviderEventId() {
        UUID tenantId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID gatewayOrderId = UUID.randomUUID();
        Map<String, Object> order = Map.of(
                "gatewayOrderId", gatewayOrderId,
                "paymentId", paymentId,
                "institutionId", institutionId,
                "orderAmount", new BigDecimal("1200.00"));
        doReturn(order).when(jdbc).query(
                anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.eq(Boolean.class)))
                .thenReturn(true);
        when(credentialsResolver.resolve(tenantId, institutionId)).thenReturn(
                new PaymentGatewayCredentialsResolver.PaymentGatewayCredentials(
                        null, null, null, "sk_test_only", "whsec_test_only"));
        when(verifier.verifyStripe(any(byte[].class), any(String.class), any(String.class), any()))
                .thenReturn(false);

        PaymentReconciliationService service = new PaymentReconciliationService(
                jdbc, new ObjectMapper(), verifier, credentialsResolver);
        byte[] raw = """
                {"id":"evt_test_123","type":"checkout.session.completed","data":{"object":{
                  "id":"cs_test_123","payment_intent":"pi_test_123","payment_status":"paid",
                  "amount_total":120000,"currency":"inr",
                  "metadata":{"institution_id":"%s","payment_id":"%s"}
                }}}
                """.formatted(institutionId, paymentId).getBytes(StandardCharsets.UTF_8);

        PaymentReconciliationService.WebhookResult result =
                service.process(tenantId, "STRIPE", raw, "t=1,v1=invalid", null);

        assertThat(result.outcome()).isEqualTo("REJECTED_SIGNATURE");
        assertThat(result.paymentId()).isEqualTo(paymentId);
        verify(verifier).verifyStripe(any(byte[].class), any(String.class), any(String.class), any());
        verifyNoMoreInteractions(jdbc);
    }
}
