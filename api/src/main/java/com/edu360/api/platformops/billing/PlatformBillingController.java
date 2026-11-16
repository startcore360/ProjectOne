package com.edu360.api.platformops.billing;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.web.bind.annotation.RequestHeader;
import com.edu360.api.idempotency.PlatformIdempotencyService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.billing.dto.ApplyDiscountRequest;
import com.edu360.api.platformops.billing.dto.CreateInvoiceRequest;
import com.edu360.api.platformops.billing.dto.InvoiceResponse;
import com.edu360.api.platformops.billing.dto.AddInvoiceLineRequest;
import com.edu360.api.platformops.billing.dto.InvoiceLineResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/invoices", "/api/platform/invoices"})
@RequiredArgsConstructor
public class PlatformBillingController {

    private final PlatformBillingService billingService;
    private final PlatformPermissionService permissionService;
    private final PlatformIdempotencyService idempotencyService;
    private final JsonMapper objectMapper;

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> listInvoices(
            @RequestParam(required = false) UUID tenantId,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.ok(billingService.listInvoices(tenantId));
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<InvoiceResponse>> listOverdueInvoices(Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.ok(billingService.listOverdueInvoices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable UUID id, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.ok(billingService.getInvoice(id));
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.createInvoice(req, principal));
    }

    @GetMapping("/{id}/lines")
    public ResponseEntity<List<InvoiceLineResponse>> listInvoiceLines(@PathVariable UUID id, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.ok(billingService.listInvoiceLines(id));
    }

    @PostMapping("/{id}/lines")
    public ResponseEntity<InvoiceResponse> addInvoiceLine(
            @PathVariable UUID id, @Valid @RequestBody AddInvoiceLineRequest req, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.ok(billingService.addOtherInvoiceLine(id, req, principal));
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    public ResponseEntity<InvoiceResponse> removeInvoiceLine(
            @PathVariable UUID id, @PathVariable UUID lineId, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.ok(billingService.removeOtherInvoiceLine(id, lineId, principal));
    }

    @PostMapping({"/{id}/discounts", "/{id}/discount"})
    @Transactional(transactionManager = "platformTransactionManager")
    public ResponseEntity<InvoiceResponse> applyDiscount(
            @PathVariable UUID id,
            @Valid @RequestBody ApplyDiscountRequest req,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "DISCOUNT_APPLY");
        PlatformIdempotencyService.Claim claim = idempotencyService.claim(principal.platformUserId(),
                "INVOICE_DISCOUNT", idempotencyKey, java.util.Map.of("invoiceId", id, "request", req));
        if (claim.replay()) {
            InvoiceResponse replay = objectMapper.convertValue(claim.responseBody(), InvoiceResponse.class);
            return ResponseEntity.status(claim.responseStatus() == null ? HttpStatus.OK : HttpStatus.valueOf(claim.responseStatus())).body(replay);
        }
        try {
            InvoiceResponse discounted = billingService.applyDiscount(id, req, principal);
            idempotencyService.complete(principal.platformUserId(), claim.id(), HttpStatus.OK.value(), discounted);
            return ResponseEntity.ok(discounted);
        } catch (RuntimeException ex) {
            idempotencyService.release(principal.platformUserId(), claim.id());
            throw ex;
        }
    }

    @PostMapping({"/{id}/overdue-flags", "/{id}/overdue-flag"})
    public ResponseEntity<InvoiceResponse> flagOverdue(
            @PathVariable UUID id,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "OVERDUE_FLAG");
        return ResponseEntity.ok(billingService.flagOverdue(id, principal));
    }

    @PostMapping("/{id}/approval")
    @Transactional(transactionManager = "platformTransactionManager")
    public ResponseEntity<InvoiceResponse> approveInvoice(
            @PathVariable UUID id, @RequestHeader("Idempotency-Key") String idempotencyKey, Authentication auth) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        PlatformIdempotencyService.Claim claim = idempotencyService.claim(principal.platformUserId(),
                "INVOICE_APPROVAL", idempotencyKey, java.util.Map.of("invoiceId", id));
        if (claim.replay()) {
            InvoiceResponse replay = objectMapper.convertValue(claim.responseBody(), InvoiceResponse.class);
            return ResponseEntity.status(claim.responseStatus() == null ? HttpStatus.OK : HttpStatus.valueOf(claim.responseStatus())).body(replay);
        }
        try {
            InvoiceResponse approved = billingService.approveInvoice(id, principal);
            idempotencyService.complete(principal.platformUserId(), claim.id(), HttpStatus.OK.value(), approved);
            return ResponseEntity.ok(approved);
        } catch (RuntimeException ex) {
            idempotencyService.release(principal.platformUserId(), claim.id());
            throw ex;
        }
    }

    @PostMapping({"/{id}/send", "/{id}/sending"})
    public ResponseEntity<InvoiceResponse> sendInvoice(
            @PathVariable UUID id,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.ok(billingService.sendInvoice(id, principal));
    }

    @PostMapping({"/{id}/record-payment", "/{id}/payment", "/{id}/payments"})
    public ResponseEntity<InvoiceResponse> recordPayment(
            @PathVariable UUID id,
            @Valid @RequestBody com.edu360.api.platformops.billing.dto.RecordPaymentRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.ok(billingService.recordPayment(id, req, principal));
    }
}
