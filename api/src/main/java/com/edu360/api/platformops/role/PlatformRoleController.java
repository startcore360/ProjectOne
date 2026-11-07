package com.edu360.api.platformops.role;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.PlatformAccessLookup;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platformops.role.dto.CreatePlatformRoleRequest;
import com.edu360.api.platformops.role.dto.MyPlatformPermissionsResponse;
import com.edu360.api.platformops.role.dto.PlatformActionCatalogItem;
import com.edu360.api.platformops.role.dto.PlatformRoleResponse;
import com.edu360.api.platformops.role.dto.PlatformRoleTemplateResponse;
import com.edu360.api.platformops.role.dto.SavePlatformRoleTemplateRequest;
import com.edu360.api.platformops.role.dto.StepInRequest;
import com.edu360.api.platformops.role.dto.StepInResponse;
import com.edu360.api.platformops.role.dto.UpdatePlatformRoleRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Roles, templates and step-ins for platform staff. These operations aren't in D5: they implement
 * TPA-01 to TPA-03 and OD-62 (docs/GOVERNANCE_DECISION_LOG.md, 2026-11-07). Managing them uses the
 * existing PLATFORM_USER_MANAGE action, because no new action code has been approved.
 */
@RestController
@RequestMapping({"/v1/platform", "/api/platform"})
@RequiredArgsConstructor
public class PlatformRoleController {

    private static final String MANAGE = "PLATFORM_USER_MANAGE";

    private final PlatformCustomRoleService roleService;
    private final PlatformRoleTemplateService templateService;
    private final PlatformStepInService stepInService;
    private final PlatformPermissionService permissionService;
    private final PlatformAccessLookup accessLookup;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformCustomRoleRepository customRoleRepository;

    /** Open to every platform role: it only describes the caller's own access. */
    @GetMapping("/me/permissions")
    public ResponseEntity<MyPlatformPermissionsResponse> myPermissions(Authentication auth) {
        PlatformPrincipal principal = principal(auth);
        String customRoleName = platformUserRepository.findById(principal.platformUserId())
                .map(user -> user.getPlatformRoleId())
                .flatMap(id -> id == null ? java.util.Optional.empty() : customRoleRepository.findById(id))
                .map(PlatformCustomRole::getRoleName)
                .orElse(null);
        return ResponseEntity.ok(new MyPlatformPermissionsResponse(
                principal.platformRole(),
                customRoleName,
                permissionService.heldActions(principal),
                permissionService.actionsNeedingStepIn(principal),
                accessLookup.activeStepIns(principal.platformUserId())));
    }

    @GetMapping("/role-actions")
    public ResponseEntity<List<PlatformActionCatalogItem>> actionCatalog(Authentication auth) {
        manage(auth);
        return ResponseEntity.ok(templateService.actionCatalog());
    }

    @GetMapping("/roles")
    public ResponseEntity<List<PlatformRoleResponse>> listRoles(Authentication auth) {
        manage(auth);
        return ResponseEntity.ok(roleService.listRoles());
    }

    @GetMapping("/roles/{id}")
    public ResponseEntity<PlatformRoleResponse> getRole(@PathVariable UUID id, Authentication auth) {
        manage(auth);
        return ResponseEntity.ok(roleService.getRole(id));
    }

    @PostMapping("/roles")
    public ResponseEntity<PlatformRoleResponse> createRole(
            @Valid @RequestBody CreatePlatformRoleRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(request, manage(auth)));
    }

    @PutMapping("/roles/{id}")
    public ResponseEntity<PlatformRoleResponse> updateRole(
            @PathVariable UUID id, @Valid @RequestBody UpdatePlatformRoleRequest request, Authentication auth) {
        return ResponseEntity.ok(roleService.updateRole(id, request, manage(auth)));
    }

    @PostMapping("/roles/{id}/activation")
    public ResponseEntity<PlatformRoleResponse> activate(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(roleService.activate(id, manage(auth)));
    }

    @PostMapping("/roles/{id}/deactivation")
    public ResponseEntity<PlatformRoleResponse> deactivate(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(roleService.deactivate(id, manage(auth)));
    }

    @PostMapping("/roles/{id}/archive")
    public ResponseEntity<PlatformRoleResponse> archive(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(roleService.archive(id, manage(auth)));
    }

    @PostMapping("/roles/{id}/actions/{actionCode}/take-back")
    public ResponseEntity<PlatformRoleResponse> takeBack(
            @PathVariable UUID id, @PathVariable String actionCode, Authentication auth) {
        return ResponseEntity.ok(roleService.takeBack(id, actionCode, manage(auth)));
    }

    @GetMapping("/role-templates")
    public ResponseEntity<List<PlatformRoleTemplateResponse>> listTemplates(Authentication auth) {
        manage(auth);
        return ResponseEntity.ok(templateService.listTemplates());
    }

    @PostMapping("/role-templates")
    public ResponseEntity<PlatformRoleTemplateResponse> createTemplate(
            @Valid @RequestBody SavePlatformRoleTemplateRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(request, manage(auth)));
    }

    @PutMapping("/role-templates/{id}")
    public ResponseEntity<PlatformRoleTemplateResponse> updateTemplate(
            @PathVariable UUID id, @Valid @RequestBody SavePlatformRoleTemplateRequest request, Authentication auth) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request, manage(auth)));
    }

    @PostMapping("/role-templates/{id}/reset")
    public ResponseEntity<PlatformRoleTemplateResponse> resetTemplate(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(templateService.resetTemplate(id, manage(auth)));
    }

    @PostMapping("/role-templates/{id}/archive")
    public ResponseEntity<PlatformRoleTemplateResponse> archiveTemplate(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(templateService.archiveTemplate(id, manage(auth)));
    }

    @PostMapping("/step-ins")
    public ResponseEntity<StepInResponse> stepIn(@Valid @RequestBody StepInRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stepInService.stepIn(request, principal(auth)));
    }

    @GetMapping("/step-ins")
    public ResponseEntity<List<StepInResponse>> recentStepIns(Authentication auth) {
        manage(auth);
        return ResponseEntity.ok(stepInService.recent());
    }

    private PlatformPrincipal manage(Authentication auth) {
        PlatformPrincipal principal = principal(auth);
        permissionService.authorize(principal, MANAGE);
        return principal;
    }

    private PlatformPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("Platform credentials required; no platform principal on this request.");
        }
        return principal;
    }
}
