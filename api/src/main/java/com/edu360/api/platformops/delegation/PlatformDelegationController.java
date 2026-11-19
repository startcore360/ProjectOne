package com.edu360.api.platformops.delegation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.idempotency.PlatformIdempotentExecutor;
import com.edu360.api.platformops.PlatformPermissionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Provides DPO endpoints for selecting and creating platform approval delegations. */
@RestController
@RequestMapping({"/v1/platform/delegations", "/api/platform/delegations"})
@RequiredArgsConstructor
public class PlatformDelegationController {

    private final PlatformDelegationService delegationService;
    private final PlatformPermissionService permissionService;
    private final PlatformIdempotentExecutor idempotentExecutor;

    @GetMapping("/candidates")
    public ResponseEntity<List<PlatformDelegationCandidate>> candidates(Authentication authentication) {
        PlatformPrincipal actor = (PlatformPrincipal) authentication.getPrincipal();
        permissionService.authorize(actor, "APPROVAL_DELEGATE");
        return ResponseEntity.ok(delegationService.candidates(actor));
    }

    @PostMapping
    public ResponseEntity<PlatformDelegationResponse> create(
            @Valid @RequestBody PlatformDelegationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        PlatformPrincipal actor = (PlatformPrincipal) authentication.getPrincipal();
        permissionService.authorize(actor, "APPROVAL_DELEGATE");
        return idempotentExecutor.execute(
                actor.platformUserId(),
                "createPlatformDelegation",
                idempotencyKey,
                request,
                HttpStatus.CREATED,
                PlatformDelegationResponse.class,
                () -> delegationService.create(request, actor));
    }
}
