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
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.billing.dto.ApplyDiscountRequest;
import com.edu360.api.platformops.billing.dto.CreateInvoiceRequest;
import com.edu360.api.platformops.billing.dto.InvoiceResponse;

class PlatformBillingControllerTest {

    private InvoiceRepository invoiceRepository;
    private AuditLogService auditLogService;
    private PlatformPermissionService permissionService;
    private PlatformBillingService billingService;
    private PlatformBillingController controller;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        auditLogService = mock(AuditLogService.class);
        permissionService = com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());
        billingService = new PlatformBillingService(invoiceRepository, org.mockito.Mockito.mock(com.edu360.api.platformops.role.PlatformAuditWriter.class));
        controller = new PlatformBillingController(billingService, permissionService);
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

        ApplyDiscountRequest req = new ApplyDiscountRequest(new BigDecimal("2000.00"), "Annual promotional rebate");
        ResponseEntity<InvoiceResponse> res = controller.applyDiscount(invoiceId, req, authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().discountAmount()).isEqualTo(new BigDecimal("2000.00"));
        assertThat(res.getBody().totalAmount()).isEqualTo(new BigDecimal("8000.00"));
    }

    @Test
    void ACT_DISCOUNT_APPLY_DENY_billingManager_RuleF4() {
        UUID invoiceId = UUID.randomUUID();
        ApplyDiscountRequest req = new ApplyDiscountRequest(new BigDecimal("1000.00"), "Rebate");

        // Billing Manager is denied applying discounts by permission service & Rule F-4
        assertThatThrownBy(() -> controller.applyDiscount(invoiceId, req, authFor(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void ACT_DISCOUNT_APPLY_GUARD_discountExceedsSubtotal() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceId)
                .subtotalAmount(new BigDecimal("5000.00"))
                .discountAmount(BigDecimal.ZERO)
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        ApplyDiscountRequest req = new ApplyDiscountRequest(new BigDecimal("6000.00"), "Too high");

        assertThatThrownBy(() -> controller.applyDiscount(invoiceId, req, authFor(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Rule ck_invoice_r2");
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
