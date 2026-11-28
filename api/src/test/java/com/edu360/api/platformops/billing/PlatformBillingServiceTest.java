package com.edu360.api.platformops.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.billing.dto.RecordPaymentRequest;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.subscription.PlatformSubscriptionService;

class PlatformBillingServiceTest {
    @Test
    void recordingPaymentActivatesInvoiceAddons() {
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        InvoiceLineRepository lines = mock(InvoiceLineRepository.class);
        BillingSettingsRepository settings = mock(BillingSettingsRepository.class);
        PlatformAuditWriter audit = mock(PlatformAuditWriter.class);
        PlatformSubscriptionService subscriptions = mock(PlatformSubscriptionService.class);
        PlatformBillingService service = new PlatformBillingService(invoices, lines, settings, audit);
        ReflectionTestUtils.setField(service, "subscriptionService", subscriptions);

        UUID invoiceId = UUID.randomUUID();
        PlatformPrincipal actor = new PlatformPrincipal(
                UUID.randomUUID(), PlatformRole.BILLING_MANAGER, "billing@example.com", false);
        Invoice invoice = Invoice.builder().invoiceId(invoiceId).tenantId(UUID.randomUUID())
                .invoiceNumber("INV-TEST").periodStart(LocalDate.now()).periodEnd(LocalDate.now())
                .subtotalAmount(new BigDecimal("100.00")).discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00")).currencyCode("INR").status("SENT")
                .createdAt(Instant.now()).createdBy(actor.platformUserId())
                .updatedAt(Instant.now()).updatedBy(actor.platformUserId()).rowVersion(1).build();
        when(invoices.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoices.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordPayment(invoiceId, new RecordPaymentRequest("MANUAL", "bank-ref"), actor);

        verify(subscriptions).activatePaidAddons(eq(invoiceId), eq(actor), any(Instant.class));
        verify(audit).record(actor, "INVOICE_PAID", "INVOICE_MANAGE", "INVOICE", invoiceId);
    }
}
