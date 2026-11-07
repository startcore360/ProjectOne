package com.edu360.api.platformops.billing;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.billing.dto.ApplyDiscountRequest;
import com.edu360.api.platformops.billing.dto.CreateInvoiceRequest;
import com.edu360.api.platformops.billing.dto.InvoiceResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/invoices", "/api/platform/invoices"})
@RequiredArgsConstructor
public class PlatformBillingController {

    private final PlatformBillingService billingService;
    private final PlatformPermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> listInvoices(
            @RequestParam(required = false) UUID tenantId,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "INVOICE_MANAGE");
        return ResponseEntity.ok(billingService.listInvoices(tenantId));
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

    @PostMapping({"/{id}/discounts", "/{id}/discount"})
    public ResponseEntity<InvoiceResponse> applyDiscount(
            @PathVariable UUID id,
            @Valid @RequestBody ApplyDiscountRequest req,
            Authentication auth
    ) {
        PlatformPrincipal principal = (PlatformPrincipal) auth.getPrincipal();
        permissionService.authorize(principal, "DISCOUNT_APPLY");
        return ResponseEntity.ok(billingService.applyDiscount(id, req, principal));
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
