package com.edu360.api.finance;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class OnlinePaymentCheckoutService {
    private static final RestClient HTTP = RestClient.create();
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final PaymentGatewayCredentialsResolver credentialsResolver;
    private final AuditLogService audit;
    @Value("${app.payments.stripe-success-url:}") private String stripeSuccessUrl;
    @Value("${app.payments.stripe-cancel-url:}") private String stripeCancelUrl;

    @Transactional
    public Map<String, Object> start(UUID tenantId, TenantPrincipal actor, CheckoutRequest request) {
        validate(request);
        List<UUID> installmentIds = request.installmentIds().stream().sorted().toList();

        List<Installment> installments = jdbc.query("""
            SELECT si.student_installment_id, si.institution_id, si.student_fee_account_id,
                   nd.net_due, si.status
              FROM app.student_installment si
              JOIN app.v_installment_net_due nd
                ON nd.tenant_id=si.tenant_id AND nd.student_installment_id=si.student_installment_id
             WHERE si.tenant_id=:tenantId
               AND si.student_fee_account_id=:accountId
               AND si.student_installment_id IN (:installmentIds)
             ORDER BY si.due_date, si.installment_no
             FOR UPDATE OF si
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("accountId", request.studentFeeAccountId())
                    .addValue("installmentIds", installmentIds),
                (rs, rowNum) -> new Installment(
                        rs.getObject("student_installment_id", UUID.class),
                        rs.getObject("institution_id", UUID.class),
                        rs.getBigDecimal("net_due"),
                        rs.getString("status")));
        if (installments.size() != installmentIds.size()) {
            throw new BusinessRuleViolationException("PAYMENT_INSTALLMENT_NOT_FOUND",
                    "Every selected installment must belong to the supplied fee account.");
        }
        if (installments.stream().anyMatch(i -> !"UNPAID".equals(i.status()) || i.netDue().signum() <= 0)) {
            throw new BusinessRuleViolationException("PAYMENT_INSTALLMENT_NOT_PAYABLE",
                    "Only unpaid installments with a positive outstanding amount can be selected.");
        }
        UUID institutionId = installments.get(0).institutionId();
        if (installments.stream().anyMatch(i -> !institutionId.equals(i.institutionId()))) {
            throw new BusinessRuleViolationException("PAYMENT_CROSS_INSTITUTION_NOT_ALLOWED",
                    "A single payment cannot combine installments from different institutions.");
        }
        RbacPrincipal rbacPrincipal = new RbacPrincipal(tenantId, actor.userAccountId());
        permissions.authorize(rbacPrincipal, "FEE_PAY_ONLINE",
                permissions.ownScope(rbacPrincipal), RbacTarget.NONE);

        BigDecimal total = installments.stream().map(Installment::netDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, java.math.RoundingMode.UNNECESSARY);
        Map<String, Object> account = jdbc.query("""
            SELECT sfa.status, sp.student_profile_id
              FROM app.student_fee_account sfa
              JOIN app.student_profile sp
                ON sp.tenant_id=sfa.tenant_id AND sp.student_profile_id=sfa.student_profile_id
             WHERE sfa.tenant_id=:tenantId AND sfa.student_fee_account_id=:accountId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("accountId", request.studentFeeAccountId()), rs -> {
                if (!rs.next()) return null;
                return Map.of("status", rs.getString("status"),
                        "studentProfileId", rs.getObject("student_profile_id", UUID.class));
            });
        if (account == null) throw new ResourceNotFoundException("Student fee account not found.");
        if (!"ACTIVE".equals(account.get("status"))) {
            throw new BusinessRuleViolationException("STUDENT_FEE_ACCOUNT_NOT_ACTIVE",
                    "Only an active student fee account can start online checkout.");
        }
        Boolean owner = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM app.student_profile sp
                 WHERE sp.tenant_id=:tenantId
                   AND sp.student_profile_id=:studentProfileId
                   AND sp.user_account_id=:actorId
                UNION ALL
                SELECT 1 FROM app.student_guardian_link sgl
                JOIN app.guardian_profile gp
                  ON gp.tenant_id=sgl.tenant_id AND gp.guardian_profile_id=sgl.guardian_profile_id
                 WHERE sgl.tenant_id=:tenantId
                   AND sgl.student_profile_id=:studentProfileId
                   AND sgl.status='ACTIVE' AND sgl.is_primary=true
                   AND gp.user_account_id=:actorId
            )
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("studentProfileId", account.get("studentProfileId"))
                    .addValue("actorId", actor.userAccountId()), Boolean.class);
        if (!Boolean.TRUE.equals(owner)) {
            throw new BusinessRuleViolationException("FEE_ACCOUNT_NOT_OWNED",
                    "Online fee payment is limited to the student's own account or the primary guardian.");
        }

        Map<String, Object> existing = findPayment(tenantId, request.requestKey());
        if (existing != null) {
            return replay(tenantId, request, installmentIds, institutionId, total, existing);
        }

        PaymentGatewayCredentialsResolver.PaymentGatewayCredentials credentials =
                credentialsResolver.resolve(tenantId, institutionId);
        String provider = request.provider().toUpperCase(java.util.Locale.ROOT);
        if ("RAZORPAY".equals(provider) && !credentials.hasRazorpay()) {
            throw new BusinessRuleViolationException("RAZORPAY_NOT_CONFIGURED",
                    "Razorpay is not configured for this institution.");
        }
        if ("STRIPE".equals(provider) && !credentials.hasStripe()) {
            throw new BusinessRuleViolationException("STRIPE_NOT_CONFIGURED",
                    "Stripe is not configured for this institution.");
        }

        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.payment
              (tenant_id, payment_id, institution_id, student_fee_account_id, paid_by_user_id,
               method, quoted_amount, status, request_key, expires_at, created_by, updated_by)
            VALUES
              (:tenantId, :paymentId, :institutionId, :accountId, :actorId,
               'GATEWAY', :amount, 'STARTED', :requestKey, :expiresAt, :actorId, :actorId)
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("paymentId", paymentId).addValue("institutionId", institutionId)
                    .addValue("accountId", request.studentFeeAccountId()).addValue("actorId", actor.userAccountId())
                    .addValue("amount", total).addValue("requestKey", request.requestKey())
                    .addValue("expiresAt", Instant.now().plusSeconds(900)));

        for (Installment installment : installments) {
            jdbc.update("""
                INSERT INTO app.payment_intent_item
                  (tenant_id, payment_intent_item_id, institution_id, payment_id,
                   student_installment_id, quoted_net_due, created_by, updated_by)
                VALUES
                  (:tenantId, :itemId, :institutionId, :paymentId,
                   :installmentId, :netDue, :actorId, :actorId)
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("itemId", UUID.randomUUID()).addValue("institutionId", institutionId)
                    .addValue("paymentId", paymentId).addValue("installmentId", installment.installmentId())
                    .addValue("netDue", installment.netDue()).addValue("actorId", actor.userAccountId()));
        }

        UUID gatewayOrderId = UUID.randomUUID();
        JsonNode providerResponse;
        String providerOrderId;
        String checkoutUrl = null;
        String keyId = null;
        try {
            if ("RAZORPAY".equals(provider)) {
                providerResponse = createRazorpayOrder(credentials, total, paymentId, tenantId, institutionId,
                        request.requestKey());
                providerOrderId = providerResponse.path("id").asText();
                keyId = credentials.razorpayKeyId();
            } else {
                providerResponse = createStripeSession(credentials, total, paymentId, tenantId, institutionId,
                        request.requestKey());
                providerOrderId = providerResponse.path("id").asText();
                checkoutUrl = providerResponse.path("url").asText(null);
            }
        } catch (BusinessRuleViolationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessRuleViolationException("PAYMENT_GATEWAY_UNAVAILABLE",
                    "The payment provider could not create a checkout session. Please retry.");
        }
        if (providerOrderId == null || providerOrderId.isBlank()
                || ("STRIPE".equals(provider) && (checkoutUrl == null || checkoutUrl.isBlank()))) {
            throw new BusinessRuleViolationException("PAYMENT_GATEWAY_INVALID_RESPONSE",
                    "The payment provider returned an incomplete checkout response.");
        }

        jdbc.update("""
            INSERT INTO app.gateway_order
              (tenant_id, gateway_order_id, institution_id, payment_id, provider,
               provider_order_id, order_amount, status, created_by, updated_by)
            VALUES
              (:tenantId, :orderId, :institutionId, :paymentId, :provider,
               :providerOrderId, :amount, 'CREATED', :actorId, :actorId)
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("orderId", gatewayOrderId).addValue("institutionId", institutionId)
                    .addValue("paymentId", paymentId).addValue("provider", provider)
                    .addValue("providerOrderId", providerOrderId).addValue("amount", total)
                    .addValue("actorId", actor.userAccountId()));
        jdbc.update("""
            UPDATE app.payment SET status='PENDING', updated_at=now(), updated_by=:actorId
             WHERE tenant_id=:tenantId AND payment_id=:paymentId AND status='STARTED'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("paymentId", paymentId).addValue("actorId", actor.userAccountId()));

        audit.record(AuditEventType.CREATE, null, actor.userAccountId().toString(), tenantId.toString(),
                "Online " + provider + " checkout started for payment " + paymentId + ".");
        return checkoutResponse(paymentId, provider, providerOrderId, total, keyId, checkoutUrl);
    }

    private Map<String, Object> replay(UUID tenantId, CheckoutRequest request, List<UUID> installmentIds,
            UUID institutionId, BigDecimal total, Map<String, Object> payment) {
        if (!request.studentFeeAccountId().equals(payment.get("accountId"))
                || !"GATEWAY".equals(payment.get("method"))
                || total.compareTo((BigDecimal) payment.get("quotedAmount")) != 0
                || !List.copyOf(installmentIds).equals(payment.get("installmentIds"))) {
            throw new BusinessRuleViolationException("IDEMPOTENCY_KEY_REUSED",
                    "The checkout request key has already been used for a different payment.");
        }
        String paymentStatus = (String) payment.get("status");
        if (!"PENDING".equals(paymentStatus)) {
            return Map.of("paymentId", payment.get("paymentId"), "status", paymentStatus);
        }
        Map<String, Object> order = jdbc.query("""
            SELECT gateway_order_id, provider, provider_order_id, order_amount
              FROM app.gateway_order
             WHERE tenant_id=:tenantId AND payment_id=:paymentId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("paymentId", payment.get("paymentId")), rs -> {
                if (!rs.next()) return null;
                return Map.of("provider", rs.getString("provider"),
                        "providerOrderId", rs.getString("provider_order_id"),
                        "amount", rs.getBigDecimal("order_amount"));
            });
        if (order == null) throw new BusinessRuleViolationException("CHECKOUT_ORDER_MISSING",
                "The existing payment has no gateway order; contact the institution.");
        if (!request.provider().equalsIgnoreCase((String) order.get("provider"))) {
            throw new BusinessRuleViolationException("IDEMPOTENCY_KEY_REUSED",
                    "The checkout request key has already been used with a different payment provider.");
        }
        PaymentGatewayCredentialsResolver.PaymentGatewayCredentials credentials =
                credentialsResolver.resolve(tenantId, institutionId);
        String provider = (String) order.get("provider");
        String keyId = "RAZORPAY".equals(provider) ? credentials.razorpayKeyId() : null;
        String checkoutUrl = null;
        if ("STRIPE".equals(provider)) {
            JsonNode session = HTTP.get().uri("https://api.stripe.com/v1/checkout/sessions/" + order.get("providerOrderId"))
                    .headers(h -> h.setBearerAuth(credentials.stripeSecretKey()))
                    .retrieve().body(JsonNode.class);
            checkoutUrl = session == null ? null : session.path("url").asText(null);
        }
        return checkoutResponse((UUID) payment.get("paymentId"), provider,
                (String) order.get("providerOrderId"), (BigDecimal) order.get("amount"), keyId, checkoutUrl);
    }

    private Map<String, Object> findPayment(UUID tenantId, String requestKey) {
        return jdbc.query("""
            SELECT payment_id, student_fee_account_id, method, quoted_amount, status
              FROM app.payment WHERE tenant_id=:tenantId AND request_key=:requestKey
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("requestKey", requestKey), rs -> {
                if (!rs.next()) return null;
                UUID paymentId = rs.getObject("payment_id", UUID.class);
                List<UUID> ids = jdbc.query("""
                    SELECT student_installment_id FROM app.payment_intent_item
                     WHERE tenant_id=:tenantId AND payment_id=:paymentId ORDER BY student_installment_id
                    """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("paymentId", paymentId),
                        (row, n) -> row.getObject(1, UUID.class));
                return Map.of("paymentId", paymentId,
                        "accountId", rs.getObject("student_fee_account_id", UUID.class),
                        "method", rs.getString("method"), "quotedAmount", rs.getBigDecimal("quoted_amount"),
                        "status", rs.getString("status"), "installmentIds", ids);
            });
    }

    private JsonNode createRazorpayOrder(PaymentGatewayCredentialsResolver.PaymentGatewayCredentials credentials,
            BigDecimal amount, UUID paymentId, UUID tenantId, UUID institutionId, String requestKey) {
        Map<String, Object> body = Map.of("amount", amount.movePointRight(2).intValueExact(),
                "currency", "INR", "receipt", paymentId.toString(),
                "notes", Map.of("tenant_id", tenantId.toString(), "institution_id", institutionId.toString(),
                        "payment_id", paymentId.toString()));
        return HTTP.post().uri("https://api.razorpay.com/v1/orders")
                .headers(h -> h.setBasicAuth(credentials.razorpayKeyId(), credentials.razorpayKeySecret()))
                .header("X-Request-Id", requestKey).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class);
    }

    private JsonNode createStripeSession(PaymentGatewayCredentialsResolver.PaymentGatewayCredentials credentials,
            BigDecimal amount, UUID paymentId, UUID tenantId, UUID institutionId, String requestKey) {
        if (stripeSuccessUrl == null || stripeSuccessUrl.isBlank()
                || stripeCancelUrl == null || stripeCancelUrl.isBlank()) {
            throw new BusinessRuleViolationException("STRIPE_RETURN_URL_NOT_CONFIGURED",
                    "Stripe success and cancellation return URLs must be configured by the platform operator.");
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        String successUrl = stripeSuccessUrl.replace("{tenantId}", tenantId.toString());
        successUrl += (successUrl.contains("?") ? "&" : "?") + "paymentId=" + paymentId;
        form.add("success_url", successUrl);
        form.add("cancel_url", stripeCancelUrl.replace("{tenantId}", tenantId.toString()));
        form.add("client_reference_id", paymentId.toString());
        form.add("line_items[0][price_data][currency]", "inr");
        form.add("line_items[0][price_data][unit_amount]", amount.movePointRight(2).intValueExact() + "");
        form.add("line_items[0][price_data][product_data][name]", "EDU 360 student fee");
        form.add("line_items[0][quantity]", "1");
        form.add("metadata[tenant_id]", tenantId.toString());
        form.add("metadata[institution_id]", institutionId.toString());
        form.add("metadata[payment_id]", paymentId.toString());
        form.add("payment_intent_data[metadata][tenant_id]", tenantId.toString());
        form.add("payment_intent_data[metadata][institution_id]", institutionId.toString());
        form.add("payment_intent_data[metadata][payment_id]", paymentId.toString());
        return HTTP.post().uri("https://api.stripe.com/v1/checkout/sessions")
                .headers(h -> h.setBearerAuth(credentials.stripeSecretKey()))
                .header("Idempotency-Key", requestKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(JsonNode.class);
    }

    private static Map<String, Object> checkoutResponse(UUID paymentId, String provider,
            String providerOrderId, BigDecimal amount, String keyId, String checkoutUrl) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("paymentId", paymentId);
        response.put("provider", provider);
        response.put("providerOrderId", providerOrderId);
        response.put("amount", amount);
        response.put("currency", "INR");
        response.put("keyId", keyId);
        response.put("checkoutUrl", checkoutUrl);
        return response;
    }

    private static void validate(CheckoutRequest request) {
        if (request == null || request.studentFeeAccountId() == null || request.installmentIds() == null
                || request.installmentIds().isEmpty() || request.installmentIds().size() > 50
                || new HashSet<>(request.installmentIds()).size() != request.installmentIds().size()
                || request.provider() == null || request.requestKey() == null
                || request.requestKey().length() < 8 || request.requestKey().length() > 100) {
            throw new BusinessRuleViolationException("PAYMENT_CHECKOUT_REQUEST_INVALID",
                    "Fee account, 1–50 distinct installments, provider and an 8–100 character request key are required.");
        }
        if (!"RAZORPAY".equalsIgnoreCase(request.provider()) && !"STRIPE".equalsIgnoreCase(request.provider())) {
            throw new BusinessRuleViolationException("PAYMENT_PROVIDER_UNSUPPORTED",
                    "Choose Razorpay or Stripe.");
        }
    }

    private record Installment(UUID installmentId, UUID institutionId, BigDecimal netDue, String status) {}
    public record CheckoutRequest(UUID studentFeeAccountId, List<UUID> installmentIds, String provider, String requestKey) {}
}
