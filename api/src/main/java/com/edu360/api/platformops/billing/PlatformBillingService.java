package com.edu360.api.platformops.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.billing.dto.ApplyDiscountRequest;
import com.edu360.api.platformops.billing.dto.CreateInvoiceRequest;
import com.edu360.api.platformops.billing.dto.InvoiceResponse;

import lombok.RequiredArgsConstructor;

@Service
public class PlatformBillingService {

    private final InvoiceRepository invoiceRepository;
    private final AuditLogService auditLogService;
    private final com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter;

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformBillingService(
            InvoiceRepository invoiceRepository,
            AuditLogService auditLogService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter
    ) {
        this.invoiceRepository = invoiceRepository;
        this.auditLogService = auditLogService;
        this.platformAuditWriter = platformAuditWriter;
    }

    public PlatformBillingService(InvoiceRepository invoiceRepository, AuditLogService auditLogService) {
        this(invoiceRepository, auditLogService, null);
    }

    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest req, PlatformPrincipal principal) {
        if (req.periodEnd().isBefore(req.periodStart())) {
            throw new BusinessRuleViolationException("Rule ck_invoice_r1: period_end must be >= period_start");
        }

        String invoiceNumber = "INV-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant now = Instant.now();

        Invoice invoice = Invoice.builder()
                .invoiceId(UUID.randomUUID())
                .tenantId(req.tenantId())
                .invoiceNumber(invoiceNumber)
                .periodStart(req.periodStart())
                .periodEnd(req.periodEnd())
                .subtotalAmount(req.subtotalAmount())
                .discountAmount(BigDecimal.ZERO)
                .discountAppliedBy(null)
                .totalAmount(req.subtotalAmount())
                .currencyCode(req.currencyCode() != null ? req.currencyCode() : "INR")
                .status("DRAFT")
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "INVOICE_CREATED", "INVOICE_MANAGE", "INVOICE", saved.getInvoiceId());
        }
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Invoice created: " + invoiceNumber + " for tenant: " + req.tenantId());
        return InvoiceResponse.fromEntity(saved);
    }

    @Transactional
    public InvoiceResponse applyDiscount(UUID invoiceId, ApplyDiscountRequest req, PlatformPrincipal principal) {
        // Rule [F-4] & MD-34: Only the Product Owner gives discounts. Billing Manager cannot give discounts.
        if (principal.platformRole() != PlatformRole.PRODUCT_OWNER) {
            throw new UnauthorizedException("Rule [F-4] / MD-34: Only Product Owner can apply discounts; Billing Manager is denied.");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        if (req.discountAmount().compareTo(invoice.getSubtotalAmount()) > 0) {
            throw new BusinessRuleViolationException("Rule ck_invoice_r2: discount_amount cannot exceed subtotal_amount");
        }

        invoice.setDiscountAmount(req.discountAmount());
        invoice.setDiscountAppliedBy(principal.platformUserId());
        invoice.setTotalAmount(invoice.getSubtotalAmount().subtract(req.discountAmount()));
        invoice.setUpdatedAt(Instant.now());
        invoice.setUpdatedBy(principal.platformUserId());

        Invoice saved = invoiceRepository.save(invoice);
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "DISCOUNT_APPLIED", "DISCOUNT_APPLY", "INVOICE", saved.getInvoiceId());
        }
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Discount of " + req.discountAmount() + " applied to invoice " + invoice.getInvoiceNumber());
        return InvoiceResponse.fromEntity(saved);
    }

    @Transactional
    public InvoiceResponse flagOverdue(UUID invoiceId, PlatformPrincipal principal) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        invoice.setOverdueFlaggedAt(Instant.now());
        invoice.setUpdatedAt(Instant.now());
        invoice.setUpdatedBy(principal.platformUserId());

        Invoice saved = invoiceRepository.save(invoice);
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "OVERDUE_FLAGGED", "OVERDUE_FLAG", "INVOICE", saved.getInvoiceId());
        }
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Invoice " + invoice.getInvoiceNumber() + " flagged as overdue by " + principal.platformRole());
        return InvoiceResponse.fromEntity(saved);
    }

    @Transactional
    public InvoiceResponse sendInvoice(UUID invoiceId, PlatformPrincipal principal) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        Instant now = Instant.now();
        invoice.setStatus("SENT");
        invoice.setSentAt(now);
        invoice.setUpdatedAt(now);
        invoice.setUpdatedBy(principal.platformUserId());

        Invoice saved = invoiceRepository.save(invoice);
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "INVOICE_SENT", "INVOICE_MANAGE", "INVOICE", saved.getInvoiceId());
        }
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Invoice " + invoice.getInvoiceNumber() + " sent to tenant");
        return InvoiceResponse.fromEntity(saved);
    }

    @Transactional
    public InvoiceResponse recordPayment(UUID invoiceId, com.edu360.api.platformops.billing.dto.RecordPaymentRequest req, PlatformPrincipal principal) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        Instant now = Instant.now();
        invoice.setStatus("PAID");
        invoice.setPaidAt(now);
        invoice.setPaymentMode(req.paymentMode());
        invoice.setPaymentReference(req.paymentReference());
        invoice.setUpdatedAt(now);
        invoice.setUpdatedBy(principal.platformUserId());

        Invoice saved = invoiceRepository.save(invoice);
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "INVOICE_PAID", "INVOICE_MANAGE", "INVOICE", saved.getInvoiceId());
        }
        auditLogService.record(AuditEventType.TENANT_SETTINGS_UPDATED, null, principal.platformUserId().toString(), null,
                "Payment recorded for invoice " + invoice.getInvoiceNumber() + " via " + req.paymentMode() + " (Ref: " + req.paymentReference() + ")");
        return InvoiceResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoices(UUID tenantId) {
        List<Invoice> list = (tenantId != null)
                ? invoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : invoiceRepository.findAllByOrderByCreatedAtDesc();
        return list.stream().map(InvoiceResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .map(InvoiceResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
    }
}
