package com.edu360.api.document.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.document.service.CertificateService;
import com.edu360.api.governance.service.NoDuesVerificationService;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/documents/certificates",
        "/api/tenants/{tenantId}/documents/certificates"})
@RequiredArgsConstructor
public class CertificateController {
    private final CertificateService certificates;

    public record CreateTemplateRequest(@NotBlank String type, @NotBlank @Size(max=100) String name,
            @NotBlank @Size(max=50000) String templateHtml) { }
    public record CreateRequest(@NotNull UUID studentId, @NotNull UUID templateId,
            @NotBlank @Size(max=1000) String reason) { }
    public record DecisionRequest(@NotBlank @Size(max=1000) String reason) { }

    @GetMapping("/templates")
    public List<CertificateService.Template> templates(@PathVariable UUID tenantId,
            @RequestParam UUID institutionId, Authentication auth) {
        return certificates.templates(principal(auth, tenantId), institutionId);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public CertificateService.Template createTemplate(@PathVariable UUID tenantId,
            @RequestParam UUID institutionId, @Valid @RequestBody CreateTemplateRequest body,
            Authentication auth) {
        return certificates.createTemplate(principal(auth, tenantId), institutionId,
                body.type(), body.name(), body.templateHtml());
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CertificateService.Request request(@PathVariable UUID tenantId,
            @Valid @RequestBody CreateRequest body, Authentication auth) {
        return certificates.request(principal(auth, tenantId), body.studentId(), body.templateId(), body.reason());
    }

    @GetMapping("/requests")
    public List<CertificateService.Request> requests(@PathVariable UUID tenantId,
            @RequestParam UUID institutionId, Authentication auth) {
        return certificates.requests(principal(auth, tenantId), institutionId);
    }

    @PostMapping("/requests/{requestId}/approve")
    public CertificateService.Request approve(@PathVariable UUID tenantId,
            @PathVariable UUID requestId, Authentication auth) {
        return certificates.approve(principal(auth, tenantId), requestId);
    }

    @PostMapping("/requests/{requestId}/prepare")
    public CertificateService.Issuance prepare(@PathVariable UUID tenantId,
            @PathVariable UUID requestId, Authentication auth) {
        return certificates.prepare(principal(auth, tenantId), requestId);
    }

    @PostMapping("/requests/{requestId}/no-dues-override")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void override(@PathVariable UUID tenantId, @PathVariable UUID requestId,
            @Valid @RequestBody DecisionRequest body, Authentication auth) {
        certificates.overrideNoDues(principal(auth, tenantId), requestId, body.reason());
    }

    @GetMapping("/issuances")
    public List<CertificateService.IssuanceSummary> issuances(@PathVariable UUID tenantId,
            @RequestParam UUID institutionId, Authentication auth) {
        return certificates.issuances(principal(auth, tenantId), institutionId);
    }

    @GetMapping(value = "/issuances/{issuanceId}/print", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> print(@PathVariable UUID tenantId,
            @PathVariable UUID issuanceId, Authentication auth) {
        String html = certificates.renderIssuance(principal(auth, tenantId), issuanceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_SECURITY_POLICY,
                        "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data:; font-src data:")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @PostMapping("/issuances/{issuanceId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID tenantId, @PathVariable UUID issuanceId,
            @Valid @RequestBody DecisionRequest body, Authentication auth) {
        certificates.revoke(principal(auth, tenantId), issuanceId, body.reason());
    }

    @GetMapping("/students/{studentId}/no-dues")
    public NoDuesVerificationService.ClearanceResult noDues(@PathVariable UUID tenantId,
            @PathVariable UUID studentId, Authentication auth) {
        TenantPrincipal principal = principal(auth, tenantId);
        return certificates.verifyNoDues(principal, studentId);
    }

    private static TenantPrincipal principal(Authentication auth, UUID tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(p.tenantId())) throw new ResourceNotFoundException("Certificate resource not found.");
        return p;
    }
}
