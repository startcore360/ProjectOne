package com.edu360.api.termlock;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.termlock.dto.LockTermRequest;
import com.edu360.api.termlock.dto.TermLockResponse;
import com.edu360.api.termlock.dto.UnlockTermRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Term Lock Management", description = "Endpoints for academic term lock and unlock [TERM_LOCK, TERM_UNLOCK]")
public class TermLockController {

    private final TermLockService termLockService;

    @GetMapping({
            "/v1/tenants/{tenantId}/institutions/{institutionId}/term-locks",
            "/api/tenants/{tenantId}/institutions/{institutionId}/term-locks"
    })
    @Operation(summary = "List term locks for an institution")
    public ResponseEntity<List<TermLockResponse>> listTermLocks(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication
    ) {
        extractPrincipal(authentication, tenantId);
        List<TermLockResponse> response = termLockService.listTermLocks(tenantId, institutionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping({
            "/v1/tenants/{tenantId}/term-locks",
            "/api/tenants/{tenantId}/term-locks"
    })
    @Operation(summary = "List term locks by institution query parameter")
    public ResponseEntity<List<TermLockResponse>> listTermLocksQuery(
            @PathVariable UUID tenantId,
            @RequestParam UUID institutionId,
            Authentication authentication
    ) {
        extractPrincipal(authentication, tenantId);
        List<TermLockResponse> response = termLockService.listTermLocks(tenantId, institutionId);
        return ResponseEntity.ok(response);
    }

    @PostMapping({
            "/v1/tenants/{tenantId}/institutions/{institutionId}/term-locks",
            "/api/tenants/{tenantId}/institutions/{institutionId}/term-locks"
    })
    @Operation(summary = "Lock term [TERM_LOCK / Action #36]")
    public ResponseEntity<TermLockResponse> lockTerm(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @Valid @RequestBody LockTermRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        TermLockResponse response = termLockService.lockTerm(tenantId, institutionId, principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping({
            "/v1/tenants/{tenantId}/institutions/{institutionId}/term-locks/{id}/unlocks",
            "/api/tenants/{tenantId}/institutions/{institutionId}/term-locks/{id}/unlocks",
            "/v1/tenants/{tenantId}/term-locks/{id}/unlocks",
            "/api/tenants/{tenantId}/term-locks/{id}/unlocks"
    })
    @Operation(summary = "Unlock term with reason [TERM_UNLOCK / Action #37]")
    public ResponseEntity<TermLockResponse> unlockTerm(
            @PathVariable UUID tenantId,
            @PathVariable(required = false) UUID institutionId,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID instId,
            @Valid @RequestBody UnlockTermRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        UUID effectiveInstitutionId = institutionId != null ? institutionId : instId;
        if (effectiveInstitutionId == null) {
            // Retrieve term lock to get institutionId
            effectiveInstitutionId = termLockService.listTermLocks(tenantId, null).stream()
                    .filter(tl -> tl.termLockId().equals(id))
                    .map(TermLockResponse::institutionId)
                    .findFirst()
                    .orElse(UUID.randomUUID());
        }
        TermLockResponse response = termLockService.unlockTerm(tenantId, effectiveInstitutionId, id, principal, request);
        return ResponseEntity.ok(response);
    }

    private TenantPrincipal extractPrincipal(Authentication authentication, UUID pathTenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Authenticated tenant principal required.");
        }
        if (!principal.tenantId().equals(pathTenantId)) {
            throw new UnauthorizedException("Tenant mismatch between path and authentication.");
        }
        return principal;
    }
}
