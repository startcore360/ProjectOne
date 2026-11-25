package com.edu360.api.finance;

import com.edu360.api.exception.BusinessRuleViolationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {
    private static final ZoneId FINANCIAL_ZONE = ZoneId.of("Asia/Kolkata");
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PaymentWebhookSignatureVerifier verifier;
    private final PaymentGatewayCredentialsResolver credentialsResolver;

    @Transactional
    public WebhookResult process(UUID tenantId, String provider, byte[] rawBody,
            String signature, String providerEventId) {
        if (tenantId == null || rawBody == null || rawBody.length == 0) {
            throw new BusinessRuleViolationException("WEBHOOK_REQUEST_INVALID", "Tenant and raw webhook body are required.");
        }
        String normalizedProvider = provider.toUpperCase(java.util.Locale.ROOT);
        if (!"RAZORPAY".equals(normalizedProvider) && !"STRIPE".equals(normalizedProvider)) {
            throw new BusinessRuleViolationException("WEBHOOK_PROVIDER_UNSUPPORTED", "Unsupported payment provider.");
        }

        final JsonNode event;
        try {
            event = mapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new BusinessRuleViolationException("WEBHOOK_PAYLOAD_INVALID", "Webhook body must be valid JSON.");
        }
        String eventId = "STRIPE".equals(normalizedProvider) ? text(event, "/id") : providerEventId;
        String eventType = text(event, "/type");
        if (eventType == null) eventType = text(event, "/event");
        if (eventId == null || eventId.isBlank() || eventId.length() > 200
                || eventType == null || eventType.isBlank() || eventType.length() > 100) {
            throw new BusinessRuleViolationException("WEBHOOK_IDENTITY_INVALID", "A valid provider event ID and event type are required.");
        }

        String providerOrderId;
        String providerPaymentId;
        String institutionText;
        String paymentText;
        long amountMinor;
        String currency;
        boolean successful;
        boolean failed;
        if ("RAZORPAY".equals(normalizedProvider)) {
            providerOrderId = text(event, "/payload/payment/entity/order_id");
            providerPaymentId = text(event, "/payload/payment/entity/id");
            institutionText = text(event, "/payload/payment/entity/notes/institution_id");
            paymentText = text(event, "/payload/payment/entity/notes/payment_id");
            amountMinor = number(event, "/payload/payment/entity/amount");
            currency = text(event, "/payload/payment/entity/currency");
            successful = "payment.captured".equals(eventType);
            failed = "payment.failed".equals(eventType);
        } else {
            JsonNode object = event.at("/data/object");
            providerOrderId = text(event, "/data/object/id");
            providerPaymentId = text(event, "/data/object/payment_intent");
            institutionText = text(event, "/data/object/metadata/institution_id");
            paymentText = text(event, "/data/object/metadata/payment_id");
            amountMinor = number(event, "/data/object/amount_total");
            if (amountMinor == 0) amountMinor = number(event, "/data/object/amount_received");
            currency = text(event, "/data/object/currency");
            successful = ("checkout.session.completed".equals(eventType)
                    || "checkout.session.async_payment_succeeded".equals(eventType)
                    || "payment_intent.succeeded".equals(eventType))
                    && (!"checkout.session.completed".equals(eventType)
                        || "paid".equals(text(event, "/data/object/payment_status")));
            failed = "checkout.session.async_payment_failed".equals(eventType)
                    || "payment_intent.payment_failed".equals(eventType);
            if (providerPaymentId == null) providerPaymentId = text(object, "/id");
        }

        UUID institutionId = parseUuid(institutionText);
        UUID paymentId = parseUuid(paymentText);
        Map<String, Object> order = findOrder(tenantId, normalizedProvider, providerOrderId, paymentId);
        if (order != null) {
            institutionId = (UUID) order.get("institutionId");
            paymentId = (UUID) order.get("paymentId");
        }
        if (institutionId == null) {
            throw new BusinessRuleViolationException("WEBHOOK_ORDER_UNMATCHED",
                    "The webhook could not be associated with a configured institution.");
        }
        Boolean knownInstitution = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM app.institution
                            WHERE tenant_id=:tenantId AND institution_id=:institutionId)
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("institutionId", institutionId), Boolean.class);
        if (!Boolean.TRUE.equals(knownInstitution)) {
            throw new BusinessRuleViolationException("WEBHOOK_ORDER_UNMATCHED",
                    "The webhook institution does not belong to this tenant.");
        }

        PaymentGatewayCredentialsResolver.PaymentGatewayCredentials credentials =
                credentialsResolver.resolve(tenantId, institutionId);
        boolean signatureValid = "RAZORPAY".equals(normalizedProvider)
                ? verifier.verifyRazorpay(rawBody, signature, credentials.razorpayWebhookSecret())
                : verifier.verifyStripe(rawBody, signature, credentials.stripeWebhookSecret(), java.time.Instant.now());

        UUID systemActorId = findSystemActor(tenantId);
        String safePayload = safePayload(eventId, eventType, providerOrderId, providerPaymentId, amountMinor, currency);
        UUID eventRowId = UUID.randomUUID();
        org.springframework.jdbc.core.ResultSetExtractor<Boolean> insertedExtractor = rs -> rs.next();
        boolean inserted = jdbc.query("""
            INSERT INTO app.payment_gateway_event
              (tenant_id, payment_gateway_event_id, institution_id, provider,
               provider_event_id, event_type, signature_verified, payload, outcome,
               created_by, updated_by)
            VALUES
              (:tenantId, :eventId, :institutionId, :provider,
               :providerEventId, :eventType, :verified, CAST(:payload AS jsonb),
               :outcome, :actorId, :actorId)
            ON CONFLICT (tenant_id, provider, provider_event_id) DO NOTHING
            RETURNING payment_gateway_event_id
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("eventId", eventRowId)
                    .addValue("institutionId", institutionId).addValue("provider", normalizedProvider)
                    .addValue("providerEventId", eventId).addValue("eventType", eventType)
                    .addValue("verified", signatureValid).addValue("payload", safePayload)
                    .addValue("outcome", signatureValid ? "NO_CHANGE" : "REJECTED_SIGNATURE")
                    .addValue("actorId", systemActorId),
                    insertedExtractor);
        if (!signatureValid) {
            if (inserted) {
                finishEvent(tenantId, eventRowId, institutionId, null, null, "REJECTED_SIGNATURE", systemActorId);
            }
            return new WebhookResult("REJECTED_SIGNATURE", paymentId, null);
        }
        if (!inserted) return new WebhookResult("IGNORED_DUPLICATE", paymentId, null);
        if (order == null || paymentId == null || providerPaymentId == null) {
            createReconciliationCase(tenantId, institutionId, paymentId, eventRowId,
                    "UNMATCHED_EVENT", systemActorId);
            finishEvent(tenantId, eventRowId, institutionId, null, null, "UNMATCHED", systemActorId);
            return new WebhookResult("UNMATCHED", paymentId, null);
        }

        UUID gatewayOrderId = (UUID) order.get("gatewayOrderId");
        BigDecimal orderAmount = (BigDecimal) order.get("orderAmount");
        UUID transactionId = null;
        String outcome = "NO_CHANGE";
        if (successful) {
            BigDecimal capturedAmount = BigDecimal.valueOf(amountMinor, 2);
            if (!"INR".equalsIgnoreCase(currency) || capturedAmount.compareTo(orderAmount) != 0) {
                createReconciliationCase(tenantId, institutionId, paymentId, eventRowId,
                        "AMOUNT_MISMATCH", systemActorId);
                outcome = "UNMATCHED";
            } else {
                transactionId = settlePayment(tenantId, institutionId, paymentId, gatewayOrderId,
                        normalizedProvider, providerPaymentId, capturedAmount, systemActorId);
                if (transactionId == null) {
                    String localStatus = findPaymentStatus(tenantId, paymentId);
                    String caseType = "PAID".equals(localStatus) ? "DUPLICATE_CAPTURE"
                            : "PENDING".equals(localStatus) ? "AMOUNT_MISMATCH" : "STATUS_DIFFERENCE";
                    createReconciliationCase(tenantId, institutionId, paymentId, eventRowId,
                            caseType, systemActorId);
                    outcome = "UNMATCHED";
                } else {
                    outcome = "APPLIED";
                }
            }
        } else if (failed) {
            boolean changed = markPaymentFailed(tenantId, paymentId, gatewayOrderId, institutionId,
                    normalizedProvider, providerPaymentId, orderAmount, systemActorId);
            if (!changed) {
                createReconciliationCase(tenantId, institutionId, paymentId, eventRowId,
                        "STATUS_DIFFERENCE", systemActorId);
            }
            outcome = changed ? "APPLIED" : "NO_CHANGE";
        }
        finishEvent(tenantId, eventRowId, institutionId, gatewayOrderId, transactionId, outcome, systemActorId);
        return new WebhookResult(outcome, paymentId, transactionId);
    }

    private UUID settlePayment(UUID tenantId, UUID institutionId, UUID paymentId, UUID gatewayOrderId,
            String provider, String providerPaymentId, BigDecimal capturedAmount, UUID actorId) {
        Map<String, Object> payment = jdbc.query("""
            SELECT status, student_fee_account_id, quoted_amount
              FROM app.payment WHERE tenant_id=:tenantId AND payment_id=:paymentId FOR UPDATE
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("paymentId", paymentId), rs -> {
                if (!rs.next()) return null;
                return Map.of("status", rs.getString("status"),
                        "accountId", rs.getObject("student_fee_account_id", UUID.class),
                        "quotedAmount", rs.getBigDecimal("quoted_amount"));
            });
        if (payment == null || !"PENDING".equals(payment.get("status"))
                || capturedAmount.compareTo((BigDecimal) payment.get("quotedAmount")) != 0) return null;

        List<SettlementItem> items = jdbc.query("""
            SELECT si.student_installment_id, si.institution_id, si.student_fee_account_id,
                   si.installment_no, si.due_date, nd.net_due, fs.academic_session_id,
                   pii.quoted_net_due
              FROM app.payment_intent_item pii
              JOIN app.student_installment si
                ON si.tenant_id=pii.tenant_id AND si.student_installment_id=pii.student_installment_id
              JOIN app.v_installment_net_due nd
                ON nd.tenant_id=si.tenant_id AND nd.student_installment_id=si.student_installment_id
              JOIN app.fee_assignment fa
                ON fa.tenant_id=si.tenant_id AND fa.fee_assignment_id=si.fee_assignment_id
              JOIN app.fee_structure fs
                ON fs.tenant_id=fa.tenant_id AND fs.fee_structure_id=fa.fee_structure_id
             WHERE pii.tenant_id=:tenantId AND pii.payment_id=:paymentId
             ORDER BY si.due_date, si.installment_no
             FOR UPDATE OF si
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("paymentId", paymentId),
                (rs, n) -> new SettlementItem(
                        rs.getObject("student_installment_id", UUID.class),
                        rs.getObject("institution_id", UUID.class),
                        rs.getObject("student_fee_account_id", UUID.class),
                        rs.getInt("installment_no"),
                        rs.getDate("due_date").toLocalDate(),
                        rs.getBigDecimal("net_due"),
                        rs.getObject("academic_session_id", UUID.class),
                        rs.getBigDecimal("quoted_net_due")));
        UUID expectedAccountId = (UUID) payment.get("accountId");
        if (items.isEmpty() || items.stream().anyMatch(i ->
                !expectedAccountId.equals(i.accountId())
                || !institutionId.equals(i.institutionId())
                || !"UNPAID".equals(readInstallmentStatus(tenantId, i.installmentId()))
                || i.netDue().compareTo(i.quotedNetDue()) != 0)) return null;
        BigDecimal allocationTotal = items.stream().map(SettlementItem::netDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocationTotal.compareTo(capturedAmount) != 0) return null;

        UUID transactionId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.gateway_transaction
              (tenant_id, gateway_transaction_id, institution_id, gateway_order_id,
               provider_payment_id, status, amount, captured_at, created_by, updated_by)
            VALUES
              (:tenantId, :transactionId, :institutionId, :orderId,
               :providerPaymentId, 'CAPTURED', :amount, now(), :actorId, :actorId)
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("transactionId", transactionId).addValue("institutionId", institutionId)
                    .addValue("orderId", gatewayOrderId).addValue("providerPaymentId", providerPaymentId)
                    .addValue("amount", capturedAmount).addValue("actorId", actorId));

        for (SettlementItem item : items) {
            jdbc.update("""
                INSERT INTO app.payment_allocation
                  (tenant_id, payment_allocation_id, institution_id, payment_id,
                   student_installment_id, allocated_amount, created_by, updated_by)
                VALUES
                  (:tenantId, :allocationId, :institutionId, :paymentId,
                   :installmentId, :amount, :actorId, :actorId)
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("allocationId", UUID.randomUUID()).addValue("institutionId", institutionId)
                    .addValue("paymentId", paymentId).addValue("installmentId", item.installmentId())
                    .addValue("amount", item.netDue()).addValue("actorId", actorId));
        }

        jdbc.update("""
            UPDATE app.student_installment
               SET status='PAID', settled_by='PAYMENT', paid_at=now(), updated_at=now(), updated_by=:actorId
             WHERE tenant_id=:tenantId AND student_installment_id IN (:installmentIds) AND status='UNPAID'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("installmentIds", items.stream().map(SettlementItem::installmentId).toList())
                    .addValue("actorId", actorId));

        Map<UUID, BigDecimal> bySession = new HashMap<>();
        items.forEach(i -> bySession.merge(i.academicSessionId(), i.netDue(), BigDecimal::add));
        LocalDate today = LocalDate.now(FINANCIAL_ZONE);
        short financialYear = (short) (today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1);
        for (Map.Entry<UUID, BigDecimal> entry : bySession.entrySet()) {
            jdbc.update("""
                INSERT INTO app.ledger_entry
                  (tenant_id, ledger_entry_id, institution_id, student_fee_account_id,
                   academic_session_id, financial_year, posting_date, entry_type, direction,
                   amount, currency_code, payment_id, narrative, created_by, updated_by)
                VALUES
                  (:tenantId, :ledgerId, :institutionId, :accountId,
                   :sessionId, :financialYear, :postingDate, 'PAYMENT', 'CREDIT',
                   :amount, 'INR', :paymentId, :narrative, :actorId, :actorId)
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("ledgerId", UUID.randomUUID()).addValue("institutionId", institutionId)
                    .addValue("accountId", payment.get("accountId")).addValue("sessionId", entry.getKey())
                    .addValue("financialYear", financialYear).addValue("postingDate", today)
                    .addValue("amount", entry.getValue()).addValue("paymentId", paymentId)
                    .addValue("narrative", "Online " + provider + " payment " + paymentId)
                    .addValue("actorId", actorId));
        }

        jdbc.update("""
            UPDATE app.payment
               SET status='PAID', amount=:amount, confirmed_via='WEBHOOK',
                   gateway_transaction_id=:transactionId, allocated_amount=:allocatedAmount,
                   credit_added_amount=0, amount_mismatch=false, paid_at=now(),
                   updated_at=now(), updated_by=:actorId
             WHERE tenant_id=:tenantId AND payment_id=:paymentId AND status='PENDING'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("paymentId", paymentId)
                    .addValue("amount", capturedAmount).addValue("transactionId", transactionId)
                    .addValue("allocatedAmount", allocationTotal).addValue("actorId", actorId));
        jdbc.update("""
            UPDATE app.gateway_order SET status='PAID', updated_at=now(), updated_by=:actorId
             WHERE tenant_id=:tenantId AND gateway_order_id=:orderId AND status='CREATED'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("orderId", gatewayOrderId).addValue("actorId", actorId));

        issueReceipt(tenantId, institutionId, paymentId, capturedAmount, actorId, today, financialYear);
        return transactionId;
    }

    private boolean markPaymentFailed(UUID tenantId, UUID paymentId, UUID orderId, UUID institutionId,
            String provider, String providerPaymentId, BigDecimal amount, UUID actorId) {
        Map<String, Object> payment = jdbc.query("""
            SELECT status FROM app.payment WHERE tenant_id=:tenantId AND payment_id=:paymentId FOR UPDATE
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("paymentId", paymentId),
                rs -> rs.next() ? Map.of("status", rs.getString(1)) : null);
        if (payment == null || !"PENDING".equals(payment.get("status"))) return false;
        UUID transactionId = null;
        if (providerPaymentId != null && !providerPaymentId.isBlank()) {
            transactionId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO app.gateway_transaction
                  (tenant_id, gateway_transaction_id, institution_id, gateway_order_id,
                   provider_payment_id, status, amount, created_by, updated_by)
                VALUES
                  (:tenantId, :transactionId, :institutionId, :orderId,
                   :providerPaymentId, 'FAILED', :amount, :actorId, :actorId)
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("transactionId", transactionId).addValue("institutionId", institutionId)
                    .addValue("orderId", orderId).addValue("providerPaymentId", providerPaymentId)
                    .addValue("amount", amount).addValue("actorId", actorId));
        }
        jdbc.update("""
            UPDATE app.payment SET status='FAILED', failure_reason=:reason, updated_at=now(), updated_by=:actorId
             WHERE tenant_id=:tenantId AND payment_id=:paymentId AND status='PENDING'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("paymentId", paymentId)
                    .addValue("reason", "Payment provider reported a failed " + provider + " attempt.")
                    .addValue("actorId", actorId));
        jdbc.update("""
            UPDATE app.gateway_order SET status='FAILED', updated_at=now(), updated_by=:actorId
             WHERE tenant_id=:tenantId AND gateway_order_id=:orderId AND status='CREATED'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("orderId", orderId).addValue("actorId", actorId));
        return true;
    }

    private void issueReceipt(UUID tenantId, UUID institutionId, UUID paymentId, BigDecimal amount,
            UUID actorId, LocalDate today, short financialYear) {
        String prefix = jdbc.query("""
            SELECT prefix FROM app.receipt_sequence
             WHERE tenant_id=:tenantId AND institution_id=:institutionId AND financial_year=:financialYear
             FOR UPDATE
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("institutionId", institutionId).addValue("financialYear", financialYear),
                rs -> rs.next() ? rs.getString(1) : null);
        if (prefix == null) throw new BusinessRuleViolationException("RECEIPT_SEQUENCE_MISSING",
                "Receipt sequence is not configured for this institution and financial year.");
        Integer number = jdbc.queryForObject("SELECT app.next_receipt_number(:tenantId,:institutionId,:financialYear)",
                new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("institutionId", institutionId).addValue("financialYear", financialYear), Integer.class);
        UUID receiptId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.receipt
              (tenant_id, receipt_id, institution_id, payment_id, financial_year,
               receipt_number, receipt_label, amount, status, created_by, updated_by)
            VALUES
              (:tenantId, :receiptId, :institutionId, :paymentId, :financialYear,
               :number, :label, :amount, 'ISSUED', :actorId, :actorId)
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("receiptId", receiptId)
                    .addValue("institutionId", institutionId).addValue("paymentId", paymentId)
                    .addValue("financialYear", financialYear).addValue("number", number)
                    .addValue("label", prefix + number).addValue("amount", amount).addValue("actorId", actorId));
    }

    private Map<String, Object> findOrder(UUID tenantId, String provider, String providerOrderId, UUID paymentId) {
        return jdbc.query("""
            SELECT gateway_order_id, payment_id, institution_id, order_amount
              FROM app.gateway_order
             WHERE tenant_id=:tenantId AND provider=:provider
               AND ((:providerOrderId IS NOT NULL AND provider_order_id=:providerOrderId)
                 OR (:paymentId IS NOT NULL AND payment_id=:paymentId))
             ORDER BY created_at DESC LIMIT 1
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("provider", provider)
                    .addValue("providerOrderId", providerOrderId).addValue("paymentId", paymentId), rs -> {
                if (!rs.next()) return null;
                return Map.of("gatewayOrderId", rs.getObject("gateway_order_id", UUID.class),
                        "paymentId", rs.getObject("payment_id", UUID.class),
                        "institutionId", rs.getObject("institution_id", UUID.class),
                        "orderAmount", rs.getBigDecimal("order_amount"));
            });
    }

    private UUID findSystemActor(UUID tenantId) {
        UUID actor = jdbc.query("""
            SELECT user_account_id FROM app.user_account
             WHERE tenant_id=:tenantId AND user_type='SYSTEM' AND status='ACTIVE'
             ORDER BY created_at LIMIT 1
            """, Map.of("tenantId", tenantId), rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (actor == null) throw new BusinessRuleViolationException("SYSTEM_ACTOR_MISSING",
                "No active SYSTEM actor is provisioned for webhook processing.");
        return actor;
    }

    private String findPaymentStatus(UUID tenantId, UUID paymentId) {
        return jdbc.query("""
            SELECT status FROM app.payment
             WHERE tenant_id=:tenantId AND payment_id=:paymentId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("paymentId", paymentId),
                rs -> rs.next() ? rs.getString(1) : null);
    }

    private void createReconciliationCase(UUID tenantId, UUID institutionId, UUID paymentId,
            UUID eventId, String caseType, UUID actorId) {
        if (institutionId == null || eventId == null) return;
        jdbc.update("""
            INSERT INTO app.reconciliation_case
              (tenant_id, reconciliation_case_id, institution_id, case_type, payment_id,
               payment_gateway_event_id, status, created_by, updated_by)
            SELECT :tenantId, :caseId, :institutionId, :caseType, :paymentId,
                   :eventId, 'OPEN', :actorId, :actorId
             WHERE NOT EXISTS (
                 SELECT 1 FROM app.reconciliation_case
                  WHERE tenant_id=:tenantId AND payment_gateway_event_id=:eventId
                    AND case_type=:caseType AND status IN ('OPEN', 'ESCALATED')
             )
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("caseId", UUID.randomUUID()).addValue("institutionId", institutionId)
                    .addValue("caseType", caseType).addValue("paymentId", paymentId)
                    .addValue("eventId", eventId).addValue("actorId", actorId));
    }

    private String readInstallmentStatus(UUID tenantId, UUID installmentId) {
        return jdbc.query("""
            SELECT status FROM app.student_installment
             WHERE tenant_id=:tenantId AND student_installment_id=:installmentId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("installmentId", installmentId),
                rs -> rs.next() ? rs.getString(1) : null);
    }

    private void finishEvent(UUID tenantId, UUID eventId, UUID institutionId, UUID orderId,
            UUID transactionId, String outcome, UUID actorId) {
        jdbc.update("""
            UPDATE app.payment_gateway_event
               SET institution_id=:institutionId, gateway_order_id=:orderId,
                   gateway_transaction_id=:transactionId, processed_at=now(), outcome=:outcome,
                   updated_at=now(), updated_by=:actorId
             WHERE tenant_id=:tenantId AND payment_gateway_event_id=:eventId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("eventId", eventId)
                    .addValue("institutionId", institutionId).addValue("orderId", orderId)
                    .addValue("transactionId", transactionId).addValue("outcome", outcome).addValue("actorId", actorId));
    }

    private String safePayload(String eventId, String eventType, String orderId, String paymentId,
            long amountMinor, String currency) {
        Map<String, Object> safe = new HashMap<>();
        safe.put("id", eventId);
        safe.put("type", eventType);
        safe.put("providerOrderId", orderId);
        safe.put("providerPaymentId", paymentId);
        safe.put("amountMinor", amountMinor);
        safe.put("currency", currency);
        try {
            return mapper.writeValueAsString(safe);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize the sanitized gateway event.");
        }
    }

    private static String text(JsonNode root, String pointer) {
        JsonNode node = root.at(pointer);
        return node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private static long number(JsonNode root, String pointer) {
        JsonNode node = root.at(pointer);
        return node.isMissingNode() || node.isNull() ? 0 : node.asLong(0);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ex) { return null; }
    }

    private record SettlementItem(UUID installmentId, UUID institutionId, UUID accountId,
            int installmentNumber, LocalDate dueDate, BigDecimal netDue, UUID academicSessionId,
            BigDecimal quotedNetDue) {}

    public record WebhookResult(String outcome, UUID paymentId, UUID gatewayTransactionId) {}
}
