package com.edu360.api.document.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.document.service.IdCardService;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/documents/id-cards",
        "/api/tenants/{tenantId}/documents/id-cards"})
@RequiredArgsConstructor
public class IdCardController {
    private final IdCardService cards;

    public record CreateTemplateRequest(@NotBlank @Size(max=100) String name,
            @NotBlank String layout, String backgroundImageRef, String fieldsConfigJson) { }
    public record IssueRequest(@NotNull UUID institutionId, @NotBlank String subjectType,
            @NotNull UUID profileId, @NotNull UUID templateId, LocalDate expiryDate) { }
    public record ReplacementRequest(@NotBlank @Size(max=1000) String reason, @NotNull UUID feeReceiptId) { }
    public record ReplacementDecision(@NotNull Boolean approve, @NotBlank @Size(max=1000) String note) { }
    public record RevokeRequest(@NotBlank @Size(max=1000) String reason) { }
    public record BatchPrintRequest(@NotNull UUID institutionId, @NotNull List<UUID> issuanceIds) { }

    @GetMapping("/templates")
    public List<IdCardService.Template> templates(@PathVariable UUID tenantId,
            @RequestParam UUID institutionId, Authentication auth) {
        return cards.templates(principal(auth, tenantId), institutionId);
    }

    @PostMapping("/templates")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public IdCardService.Template createTemplate(@PathVariable UUID tenantId,
            @RequestParam UUID institutionId, @Valid @RequestBody CreateTemplateRequest body,
            Authentication auth) {
        return cards.createTemplate(principal(auth, tenantId), institutionId, body.name(),
                body.layout(), body.backgroundImageRef(), body.fieldsConfigJson());
    }

    @PutMapping("/templates/{templateId}")
    public IdCardService.Template updateTemplate(@PathVariable UUID tenantId,
            @PathVariable UUID templateId, @RequestParam int expectedVersion,
            @Valid @RequestBody CreateTemplateRequest body, Authentication auth) {
        return cards.updateTemplate(principal(auth, tenantId), templateId, body.name(),
                body.layout(), body.backgroundImageRef(), body.fieldsConfigJson(), expectedVersion);
    }

    @PostMapping("/issuances")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public IdCardService.Card issue(@PathVariable UUID tenantId,
            @Valid @RequestBody IssueRequest body, Authentication auth) {
        return cards.issue(principal(auth, tenantId), body.institutionId(), body.subjectType(),
                body.profileId(), body.templateId(), body.expiryDate());
    }

    @GetMapping("/issuances")
    public List<IdCardService.Card> list(@PathVariable UUID tenantId,
            @RequestParam UUID institutionId, Authentication auth) {
        return cards.cards(principal(auth, tenantId), institutionId);
    }

    @PostMapping("/issuances/{issuanceId}/replacements")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public IdCardService.Replacement requestReplacement(@PathVariable UUID tenantId,
            @PathVariable UUID issuanceId, @Valid @RequestBody ReplacementRequest body, Authentication auth) {
        return cards.requestReplacement(principal(auth, tenantId), issuanceId, body.reason(), body.feeReceiptId());
    }

    @GetMapping("/replacements")
    public List<IdCardService.Replacement> replacementRequests(@PathVariable UUID tenantId,
            @RequestParam UUID institutionId, Authentication auth) {
        return cards.replacementRequests(principal(auth, tenantId), institutionId);
    }

    @PostMapping("/replacements/{replacementId}/decision")
    public IdCardService.Replacement decideReplacement(@PathVariable UUID tenantId,
            @PathVariable UUID replacementId, @Valid @RequestBody ReplacementDecision body, Authentication auth) {
        return cards.decideReplacement(principal(auth, tenantId), replacementId, body.approve(), body.note());
    }

    @PostMapping("/issuances/{issuanceId}/revoke")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID tenantId, @PathVariable UUID issuanceId,
            @Valid @RequestBody RevokeRequest body, Authentication auth) {
        cards.revoke(principal(auth, tenantId), issuanceId, body.reason());
    }

    @GetMapping(value = "/issuances/{issuanceId}/print.svg", produces = "image/svg+xml")
    public ResponseEntity<String> svg(@PathVariable UUID tenantId,
            @PathVariable UUID issuanceId, Authentication auth) {
        String svg = cards.svg(principal(auth, tenantId), issuanceId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename("identity-card.svg").build().toString())
                .contentType(MediaType.valueOf("image/svg+xml")).body(svg);
    }

    @PostMapping(value = "/print/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> batchPdf(@PathVariable UUID tenantId,
            @Valid @RequestBody BatchPrintRequest body, Authentication auth) {
        byte[] pdf = cards.batchPdf(principal(auth, tenantId), body.institutionId(), body.issuanceIds());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("identity-cards.pdf").build().toString())
                .body(pdf);
    }

    private static TenantPrincipal principal(Authentication auth, UUID tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(p.tenantId())) throw new ResourceNotFoundException("Identity-card resource not found.");
        return p;
    }
}
