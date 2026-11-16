package com.edu360.api.platformops.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.idempotency.PlatformIdempotencyService;
import tools.jackson.databind.json.JsonMapper;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.billing.dto.ApplyDiscountRequest;
import com.edu360.api.platformops.billing.dto.CreateInvoiceRequest;
import com.edu360.api.platformops.billing.dto.InvoiceResponse;
import com.edu360.api.platformops.billing.dto.AddInvoiceLineRequest;
import com.edu360.api.platformops.billing.dto.InvoiceLineResponse;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import static org.mockito.ArgumentMatchers.eq;

class PlatformBillingControllerTest {

    private InvoiceRepository invoiceRepository;
    private InvoiceLineRepository invoiceLineRepository;
    private AuditLogService auditLogService;
    private PlatformPermissionService permissionService;
    private PlatformBillingService billingService;
    private PlatformIdempotencyService idempotencyService;
    private PlatformAuditWriter platformAuditWriter;
    private PlatformBillingController controller;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        invoiceLineRepository = mock(InvoiceLineRepository.class);
        idempotencyService = mock(PlatformIdempotencyService.class);
        platformAuditWriter = mock(PlatformAuditWriter.class);
        auditLogService = mock(AuditLogService.class);
        permissionService = com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());
        billingService = new PlatformBillingService(invoiceRepository, invoiceLineRepository, platformAuditWriter);
        controller = new PlatformBillingController(billingService, permissionService, idempotencyService, JsonMapper.builder().findAndAddModules().build());
    }

    private Authentication authFor(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(USER_ID, role, "billing@edu360.local", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void ACT_INVOICE_MANAGE_ALLOW() {
        CreateInvoiceRequest req = new CreateInvoiceRequest(
                TENANT_ID,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("50000.00"),
                "INR"
        );

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<InvoiceResponse> res = controller.createInvoice(req, authFor(PlatformRole.BILLING_MANAGER));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().subtotalAmount()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(res.getBody().discountAmount()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void ACT_DISCOUNT_APPLY_ALLOW_productOwner() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceId)
                .tenantId(TENANT_ID)
                .invoiceNumber("INV-2026-001")
                .periodStart(LocalDate.of(2026, 1, 1))
                .periodEnd(LocalDate.of(2026, 1, 31))
                .subtotalAmount(new BigDecimal("10000.00"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("10000.00"))
                .currencyCode("INR")
                .status("DRAFT")
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        when(idempotencyService.claim(any(), any(), any(), any())).thenReturn(new PlatformIdempotencyService.Claim(UUID.randomUUID(), false, null, null));
        ApplyDiscountRequest req = new ApplyDiscountRequest(new BigDecimal("20.00"), "Annual promotional rebate");
        ResponseEntity<InvoiceResponse> res = controller.applyDiscount(invoiceId, req, "discount-key-001", authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().discountAmount()).isEqualTo(new BigDecimal("2000.00"));
        assertThat(res.getBody().totalAmount()).isEqualTo(new BigDecimal("8000.00"));
        verify(idempotencyService).complete(any(), any(), eq(200), any());
        verify(platformAuditWriter).record(any(PlatformPrincipal.class), org.mockito.ArgumentMatchers.isNull(),
                eq("DISCOUNT_APPLIED"), eq("UPDATE"), eq("DISCOUNT_APPLY"), eq("INVOICE"), eq(invoiceId), eq(PlatformAuditWriter.SUCCESS),
                org.mockito.ArgumentMatchers.argThat(changes -> "Annual promotional rebate".equals(changes.get("reason").get("new"))));
    }


    @Test
    void ACT_DISCOUNT_APPLY_ALLOW_approvedInvoice() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder().invoiceId(invoiceId).subtotalAmount(new BigDecimal("50000.00"))
                .discountAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("50000.00")).status("APPROVED").build();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyService.claim(any(), any(), any(), any())).thenReturn(
                new PlatformIdempotencyService.Claim(UUID.randomUUID(), false, null, null));
        ResponseEntity<InvoiceResponse> response = controller.applyDiscount(invoiceId,
                new ApplyDiscountRequest(new BigDecimal("10.00"), "Renewal incentive"),
                "discount-key-approved", authFor(PlatformRole.PRODUCT_OWNER));
        assertThat(response.getBody().discountAmount()).isEqualByComparingTo("5000.00");
        assertThat(response.getBody().totalAmount()).isEqualByComparingTo("45000.00");
    }

    @Test
    void ACT_DISCOUNT_APPLY_DENY_billingManager_RuleF4() {
        UUID invoiceId = UUID.randomUUID();
        ApplyDiscountRequest req = new ApplyDiscountRequest(new BigDecimal("10.00"), "Rebate");

        // Billing Manager is denied applying discounts by permission service & Rule F-4
        assertThatThrownBy(() -> controller.applyDiscount(invoiceId, req, "discount-key-deny", authFor(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void ACT_DISCOUNT_APPLY_GUARD_discountExceedsSubtotal() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceId)
                .subtotalAmount(new BigDecimal("5000.00"))
                .discountAmount(BigDecimal.ZERO)
                .status("DRAFT")
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        ApplyDiscountRequest req = new ApplyDiscountRequest(new BigDecimal("101.00"), "Too high");

        when(idempotencyService.claim(any(), any(), any(), any())).thenReturn(new PlatformIdempotencyService.Claim(UUID.randomUUID(), false, null, null));
        assertThatThrownBy(() -> controller.applyDiscount(invoiceId, req, "discount-key-002", authFor(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("between 0.01 and 100");
    }


    @Test
    void ACT_INVOICE_LINES_ALLOW_listAndAddOtherLine_recalculatesSubtotal() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder().invoiceId(invoiceId).tenantId(TENANT_ID).invoiceNumber("INV-LINES")
                .periodStart(LocalDate.of(2026, 1, 1)).periodEnd(LocalDate.of(2026, 1, 31))
                .subtotalAmount(new BigDecimal("100.00")).discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00")).currencyCode("INR").status("DRAFT").build();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceLineRepository.saveAndFlush(any(InvoiceLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceLineRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId)).thenAnswer(inv -> {
            InvoiceLine line = org.mockito.Mockito.mockingDetails(invoiceLineRepository).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("saveAndFlush"))
                    .map(call -> (InvoiceLine) call.getArgument(0)).findFirst().orElseThrow();
            return List.of(line);
        });
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<InvoiceResponse> response = controller.addInvoiceLine(invoiceId,
                new AddInvoiceLineRequest("Additional onboarding support", new BigDecimal("2"), new BigDecimal("25.00")),
                authFor(PlatformRole.BILLING_MANAGER));

        assertThat(response.getBody().subtotalAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getBody().totalAmount()).isEqualByComparingTo("50.00");
        verify(invoiceLineRepository).saveAndFlush(any(InvoiceLine.class));
    }

    @Test
    void ACT_INVOICE_LINES_DENY_sentInvoiceCannotBeEdited() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(Invoice.builder()
                .invoiceId(invoiceId).status("SENT").discountAmount(BigDecimal.ZERO).build()));
        assertThatThrownBy(() -> controller.addInvoiceLine(invoiceId,
                new AddInvoiceLineRequest("Late fee", BigDecimal.ONE, BigDecimal.TEN),
                authFor(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(com.edu360.api.exception.ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVOICE_NOT_DRAFT");
        org.mockito.Mockito.verifyNoInteractions(invoiceLineRepository);
    }

    @Test
    void ACT_INVOICE_LINES_GUARD_onlyOtherLinesCanBeRemoved() {
        UUID invoiceId = UUID.randomUUID(), lineId = UUID.randomUUID();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(Invoice.builder()
                .invoiceId(invoiceId).status("DRAFT").discountAmount(BigDecimal.ZERO).build()));
        when(invoiceLineRepository.findByInvoiceLineIdAndInvoiceId(lineId, invoiceId)).thenReturn(Optional.of(
                InvoiceLine.builder().invoiceLineId(lineId).invoiceId(invoiceId).lineType("PLAN").lineAmount(BigDecimal.TEN).build()));
        assertThatThrownBy(() -> controller.removeInvoiceLine(invoiceId, lineId, authFor(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(com.edu360.api.exception.ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVOICE_LINE_NOT_REMOVABLE");
        org.mockito.Mockito.verify(invoiceLineRepository, org.mockito.Mockito.never()).delete(any(InvoiceLine.class));
    }


    @Test
    void ACT_INVOICE_APPROVAL_ALLOW_productOwner() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder().invoiceId(invoiceId).status("DRAFT").build();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyService.claim(any(), any(), any(), any())).thenReturn(
                new PlatformIdempotencyService.Claim(UUID.randomUUID(), false, null, null));
        ResponseEntity<InvoiceResponse> response = controller.approveInvoice(invoiceId, "approve-key-001", authFor(PlatformRole.PRODUCT_OWNER));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("APPROVED");
        assertThat(response.getBody().approvedBy()).isEqualTo(USER_ID);
        assertThat(response.getBody().approvedAt()).isNotNull();
        verify(idempotencyService).complete(any(), any(), org.mockito.ArgumentMatchers.eq(200), any());
    }

    @Test
    void ACT_INVOICE_APPROVAL_DENY_billingManager() {
        UUID invoiceId = UUID.randomUUID();
        when(idempotencyService.claim(any(), any(), any(), any())).thenReturn(
                new PlatformIdempotencyService.Claim(UUID.randomUUID(), false, null, null));
        assertThatThrownBy(() -> controller.approveInvoice(invoiceId, "approve-key-002", authFor(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
        verify(idempotencyService).release(any(), any());
    }


    @Test
    void ACT_INVOICE_APPROVAL_GUARD_onlyDraftInvoicesCanBeApproved() {
        UUID invoiceId = UUID.randomUUID();
        when(idempotencyService.claim(any(), any(), any(), any())).thenReturn(
                new PlatformIdempotencyService.Claim(UUID.randomUUID(), false, null, null));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(
                Invoice.builder().invoiceId(invoiceId).status("SENT").build()));
        assertThatThrownBy(() -> controller.approveInvoice(invoiceId, "approve-key-003", authFor(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(com.edu360.api.exception.ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVOICE_NOT_DRAFT");
        verify(idempotencyService).release(any(), any());
    }

    @Test
    void ACT_INVOICE_SEND_GUARD_requiresProductOwnerApproval() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(Invoice.builder().invoiceId(invoiceId).status("DRAFT").build()));
        assertThatThrownBy(() -> controller.sendInvoice(invoiceId, authFor(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(com.edu360.api.exception.ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", "APPROVAL_REQUIRED");
    }

    @Test
    void ACT_INVOICE_SEND_ALLOW_afterApproval() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder().invoiceId(invoiceId).status("APPROVED").build();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        ResponseEntity<InvoiceResponse> response = controller.sendInvoice(invoiceId, authFor(PlatformRole.BILLING_MANAGER));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("SENT");
        assertThat(response.getBody().sentAt()).isNotNull();
        assertThat(response.getBody().dueAt()).isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(15));
    }


    @Test
    void ACT_DISCOUNT_APPLY_GUARD_sentInvoiceIsNotDiscountable() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(Invoice.builder()
                .invoiceId(invoiceId).status("SENT").subtotalAmount(new BigDecimal("100.00"))
                .discountAmount(BigDecimal.ZERO).build()));
        when(idempotencyService.claim(any(), any(), any(), any())).thenReturn(
                new PlatformIdempotencyService.Claim(UUID.randomUUID(), false, null, null));
        assertThatThrownBy(() -> controller.applyDiscount(invoiceId,
                new ApplyDiscountRequest(new BigDecimal("10"), "Late discount"),
                "discount-key-003", authFor(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(com.edu360.api.exception.ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVOICE_NOT_DISCOUNTABLE");
        verify(idempotencyService).release(any(), any());
    }

    @Test
    void ACT_OVERDUE_FLAG_ALLOW() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceId)
                .invoiceNumber("INV-2026-001")
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<InvoiceResponse> res = controller.flagOverdue(invoiceId, authFor(PlatformRole.BILLING_MANAGER));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().overdueFlaggedAt()).isNotNull();
    }
}
