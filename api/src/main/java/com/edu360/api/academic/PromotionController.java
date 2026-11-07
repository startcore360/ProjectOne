package com.edu360.api.academic;

import com.edu360.api.config.TenantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Promotion Runs", description = "Year-end student promotions [PROMOTION_RUN, PROMOTION_APPROVE / Actions #39 & #40]")
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping({"/v1/tenants/{tenantId}/promotion-runs", "/api/tenants/{tenantId}/promotion-runs"})
    @Operation(summary = "Create year-end promotion run [PROMOTION_RUN / Action #39]")
    public ResponseEntity<PromotionRunResponse> createPromotionRun(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreatePromotionRunRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.createPromotionRun(tenantId, principal, request));
    }

    @GetMapping({"/v1/tenants/{tenantId}/promotion-runs/{id}", "/api/tenants/{tenantId}/promotion-runs/{id}"})
    @Operation(summary = "Get promotion run by ID")
    public ResponseEntity<PromotionRunResponse> getPromotionRun(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(promotionService.getPromotionRun(tenantId, id));
    }

    @PostMapping({"/v1/tenants/{tenantId}/promotion-runs/{id}/submission", "/api/tenants/{tenantId}/promotion-runs/{id}/submission"})
    @Operation(summary = "Submit promotion run for approval")
    public ResponseEntity<PromotionRunResponse> submitPromotionRun(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(promotionService.submitPromotionRun(tenantId, principal, id));
    }

    @PostMapping({"/v1/tenants/{tenantId}/promotion-runs/{id}/decisions", "/api/tenants/{tenantId}/promotion-runs/{id}/decisions"})
    @Operation(summary = "Approve or reject promotion run [PROMOTION_APPROVE / Action #40]")
    public ResponseEntity<PromotionRunResponse> decidePromotionRun(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody PromotionDecisionRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(promotionService.decidePromotionRun(tenantId, principal, id, request));
    }

    @PostMapping({"/v1/tenants/{tenantId}/promotion-runs/{id}/execution", "/api/tenants/{tenantId}/promotion-runs/{id}/execution"})
    @Operation(summary = "Execute approved promotion run")
    public ResponseEntity<PromotionRunResponse> executePromotionRun(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(promotionService.executePromotionRun(tenantId, principal, id));
    }

    private TenantPrincipal extractPrincipal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal p)) {
            throw new AccessDeniedException("Authentication required");
        }
        if (!tenantId.equals(p.tenantId())) {
            throw new AccessDeniedException("Cross-tenant access forbidden");
        }
        return p;
    }
}
