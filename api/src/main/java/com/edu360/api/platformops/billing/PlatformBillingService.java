package com.edu360.api.platformops.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.billing.dto.ApplyDiscountRequest;
import com.edu360.api.platformops.billing.dto.CreateInvoiceRequest;
import com.edu360.api.platformops.billing.dto.InvoiceResponse;
import com.edu360.api.platformops.billing.dto.AddInvoiceLineRequest;
import com.edu360.api.platformops.billing.dto.InvoiceLineResponse;

import lombok.RequiredArgsConstructor;

@Service
public class PlatformBillingService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter;

    @Autowired
    public PlatformBillingService(
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.platformAuditWriter = platformAuditWriter;
    }

    /** Compatibility constructor for existing callers that do not use invoice-line operations. */
    public PlatformBillingService(
            InvoiceRepository invoiceRepository,
            com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter
    ) {
        this(invoiceRepository, null, platformAuditWriter);
    }

    @Transactional(readOnly = true)
    public List<InvoiceLineResponse> listInvoiceLines(UUID invoiceId) {
        invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        return invoiceLineRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId).stream()
                .map(InvoiceLineResponse::fromEntity).toList();
    }

    @Transactional
    public InvoiceResponse addOtherInvoiceLine(UUID invoiceId, AddInvoiceLineRequest req, PlatformPrincipal principal) {
        Invoice invoice = requireDraftInvoice(invoiceId);
        Instant now = Instant.now();
        BigDecimal quantity = req.quantity().setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitAmount = req.unitAmount().setScale(2, RoundingMode.HALF_UP);
        InvoiceLine line = InvoiceLine.builder()
                .invoiceLineId(UUID.randomUUID())
                .invoiceId(invoiceId)
                .lineType("OTHER")
                .description(req.description().trim())
                .quantity(quantity)
                .unitAmount(unitAmount)
                .lineAmount(quantity.multiply(unitAmount).setScale(2, RoundingMode.HALF_UP))
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();
        invoiceLineRepository.saveAndFlush(line);
        Invoice saved = recalculateInvoiceSubtotal(invoice, principal);
        platformAuditWriter.record(principal, "INVOICE_LINE_ADDED", "INVOICE_MANAGE", "INVOICE_LINE", line.getInvoiceLineId());
        return InvoiceResponse.fromEntity(saved);
    }

    @Transactional
    public InvoiceResponse removeOtherInvoiceLine(UUID invoiceId, UUID lineId, PlatformPrincipal principal) {
        Invoice invoice = requireDraftInvoice(invoiceId);
        InvoiceLine line = invoiceLineRepository.findByInvoiceLineIdAndInvoiceId(lineId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice line not found: " + lineId));
        if (!"OTHER".equals(line.getLineType())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVOICE_LINE_NOT_REMOVABLE",
                    "Only OTHER invoice lines can be removed.");
        }
        invoiceLineRepository.delete(line);
        invoiceLineRepository.flush();
        Invoice saved = recalculateInvoiceSubtotal(invoice, principal);
        platformAuditWriter.record(principal, "INVOICE_LINE_REMOVED", "INVOICE_MANAGE", "INVOICE_LINE", lineId);
        return InvoiceResponse.fromEntity(saved);
    }

    private Invoice requireDraftInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        if (!"DRAFT".equals(invoice.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVOICE_NOT_DRAFT",
                    "Invoice lines can only be changed while the invoice is DRAFT.");
        }
        return invoice;
    }

    private Invoice recalculateInvoiceSubtotal(Invoice invoice, PlatformPrincipal principal) {
        BigDecimal subtotal = invoiceLineRepository.findByInvoiceIdOrderByCreatedAtAsc(invoice.getInvoiceId()).stream()
                .map(InvoiceLine::getLineAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().compareTo(subtotal) > 0) {
            throw new BusinessRuleViolationException("DISCOUNT_EXCEEDS_SUBTOTAL",
                    "The invoice line change would make the existing discount exceed the new subtotal.");
        }
        invoice.setSubtotalAmount(subtotal);
        invoice.setTotalAmount(subtotal.subtract(invoice.getDiscountAmount() == null ? BigDecimal.ZERO : invoice.getDiscountAmount()));
        invoice.setUpdatedAt(Instant.now());
        invoice.setUpdatedBy(principal.platformUserId());
        return invoiceRepository.save(invoice);
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
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        platformAuditWriter.record(principal, "CREATE", "INVOICE_MANAGE", "INVOICE", saved.getInvoiceId());
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
        platformAuditWriter.record(principal, "DISCOUNT_APPLIED", "DISCOUNT_APPLY", "INVOICE", saved.getInvoiceId());
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
        platformAuditWriter.record(principal, "OVERDUE_FLAGGED", "OVERDUE_FLAG", "INVOICE", saved.getInvoiceId());
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
        platformAuditWriter.record(principal, "INVOICE_SENT", "INVOICE_MANAGE", "INVOICE", saved.getInvoiceId());
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
        platformAuditWriter.record(principal, "INVOICE_PAID", "INVOICE_MANAGE", "INVOICE", saved.getInvoiceId());
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
