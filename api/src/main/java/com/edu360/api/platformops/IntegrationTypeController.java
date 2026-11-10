package com.edu360.api.platformops;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.dto.CreateIntegrationTypeRequest;
import com.edu360.api.platformops.dto.IntegrationTypeResponse;
import com.edu360.api.platformops.dto.UpdateIntegrationTypeRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * INTEGRATION_TYPES_MANAGE - "Manage the list of allowed integration types." Who: Product Owner,
 * Platform Admin (sql/05_ref_data.sql ids 1682/1683). Manages ONLY the TYPE catalogue
 * (platform.integration_type - e.g. "SMS_DLT", "RAZORPAY" as allowed category codes). Never a
 * tenant's actual keys/webhooks: this controller has no dependency on app.channel_config (the
 * tenant-scoped table that holds the actual, secret-by-reference integration config) and no
 * endpoint here ever returns a secret_ref or any tenant-identifiable value.
 *
 * No If-Match / optimistic-concurrency header handling in this slice: PATCH applies unconditional
 * last-write-wins on the fields supplied. The DB's own row_version/trg_touch machinery still
 * protects the row itself; wiring true If-Match semantics through the API layer (the D5 pattern
 * used by the tenant-scoped write endpoints) is a deliberate scope cut for this read-mostly
 * platform package, flagged here rather than silently assumed.
 */
@RestController
@RequestMapping({"/v1/platform/integration-types", "/api/platform/integration-types"})
@RequiredArgsConstructor
@Tag(name = "Platform Ops", description = "Platform Admin / Product Owner read-mostly operations")
public class IntegrationTypeController {

    private final PlatformPermissionService platformPermissionService;
    private final IntegrationTypeRepository integrationTypeRepository;
    private final com.edu360.api.platformops.role.PlatformAuditWriter auditWriter;

    @GetMapping
    @Operation(summary = "List allowed integration types (INTEGRATION_TYPES_MANAGE)")
    public ResponseEntity<List<IntegrationTypeResponse>> list(Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "INTEGRATION_TYPES_MANAGE");

        List<IntegrationTypeResponse> response = integrationTypeRepository.findAllByOrderByIntegrationTypeCode()
                .stream().map(IntegrationTypeResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Add a new allowed integration type (INTEGRATION_TYPES_MANAGE)")
    public ResponseEntity<IntegrationTypeResponse> create(
            @Valid @RequestBody CreateIntegrationTypeRequest request,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "INTEGRATION_TYPES_MANAGE");

        if (integrationTypeRepository.findByIntegrationTypeCode(request.integrationTypeCode()).isPresent()) {
            throw new IllegalArgumentException(
                    "An integration type with code " + request.integrationTypeCode() + " already exists.");
        }

        IntegrationType entity = IntegrationType.builder()
                .integrationTypeId(UUID.randomUUID())
                .integrationTypeCode(request.integrationTypeCode())
                .description(request.description())
                .status("ALLOWED")
                .allowedProviders(request.allowedProviders() == null ? List.of() : request.allowedProviders())
                .createdBy(principal.platformUserId())
                .updatedBy(principal.platformUserId())
                .build();

        IntegrationType saved = integrationTypeRepository.save(entity);
        auditWriter.record(principal, "INTEGRATION_TYPE_CREATED", "INTEGRATION_TYPES_MANAGE", "INTEGRATION_TYPE", saved.getIntegrationTypeId());
        return ResponseEntity.ok(IntegrationTypeResponse.from(saved));
    }

    @PatchMapping("/{integrationTypeId}")
    @Operation(summary = "Update an allowed integration type (INTEGRATION_TYPES_MANAGE)")
    public ResponseEntity<IntegrationTypeResponse> update(
            @PathVariable UUID integrationTypeId,
            @Valid @RequestBody UpdateIntegrationTypeRequest request,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        platformPermissionService.authorize(principal, "INTEGRATION_TYPES_MANAGE");

        IntegrationType entity = integrationTypeRepository.findById(integrationTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Integration type " + integrationTypeId + " not found."));

        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.allowedProviders() != null) {
            entity.setAllowedProviders(request.allowedProviders());
        }
        entity.setUpdatedBy(principal.platformUserId());

        IntegrationType saved = integrationTypeRepository.save(entity);
        auditWriter.record(principal, "INTEGRATION_TYPE_UPDATED", "INTEGRATION_TYPES_MANAGE", "INTEGRATION_TYPE", saved.getIntegrationTypeId());
        return ResponseEntity.ok(IntegrationTypeResponse.from(saved));
    }

    private PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("INTEGRATION_TYPES_MANAGE is a platform-side action; no platform principal on this request.");
        }
        return principal;
    }
}
