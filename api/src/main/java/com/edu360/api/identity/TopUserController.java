package com.edu360.api.identity;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.dto.TopUserCreateRequest;
import com.edu360.api.identity.dto.TopUserResponse;
import com.edu360.api.rbac.RbacPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing endpoint for:
 * - TENANT_TOP_USERS_CREATE (Sprint 1 Story #23 / Action #268: F-12, C6, C9)
 *
 * Mapped to both /v1/tenants/{tenantId}/top-users and /api/tenants/{tenantId}/top-users.
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}/top-users", "/api/tenants/{tenantId}/top-users"})
@RequiredArgsConstructor
@Tag(name = "Top governance users", description = "Create top governance accounts (TENANT_TOP_USERS_CREATE / Rule F-12, C6)")
public class TopUserController {

    private final TopUserService topUserService;

    @PostMapping
    @Operation(summary = "Create Management, Data Protection Contact, Vice Chancellor or Governing Body Approver (TENANT_TOP_USERS_CREATE)")
    public ResponseEntity<TopUserResponse> createTopUser(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TopUserCreateRequest request,
            Authentication authentication) {

        RbacPrincipal principal = extractTenantPrincipal(tenantId, authentication);
        TopUserResponse response = topUserService.createTopUser(principal, tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private RbacPrincipal extractTenantPrincipal(UUID tenantId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Top user creation requires authenticated tenant principal.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the path does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}
