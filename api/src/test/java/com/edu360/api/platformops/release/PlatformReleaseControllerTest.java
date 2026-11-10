package com.edu360.api.platformops.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.release.dto.CreateReleaseRequest;
import com.edu360.api.platformops.release.dto.ReleaseResponse;
import com.edu360.api.platformops.release.dto.ReleaseTransitionRequest;

class PlatformReleaseControllerTest {

    private PlatformReleaseRepository releaseRepository;
    private AuditLogService auditLogService;
    private PlatformPermissionService permissionService;
    private PlatformReleaseService releaseService;
    private PlatformReleaseController controller;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        releaseRepository = mock(PlatformReleaseRepository.class);
        auditLogService = mock(AuditLogService.class);
        permissionService = com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());
        releaseService = new PlatformReleaseService(releaseRepository, org.mockito.Mockito.mock(com.edu360.api.platformops.role.PlatformAuditWriter.class));
        controller = new PlatformReleaseController(releaseService, permissionService);
    }

    private Authentication authFor(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(USER_ID, role, "release@edu360.local", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void ACT_RELEASE_MANAGE_ALLOW() {
        CreateReleaseRequest req = new CreateReleaseRequest("v4.0.0", "Release 4.0.0 GA", false, "v4.0.0");

        when(releaseRepository.findByVersionLabel("v4.0.0")).thenReturn(Optional.empty());
        when(releaseRepository.save(any(PlatformRelease.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<ReleaseResponse> res = controller.createRelease(req, authFor(PlatformRole.RELEASE_MANAGER));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().status()).isEqualTo("UPLOADED");
    }

    @Test
    void ACT_RELEASE_MANAGE_GUARD_duplicateVersionThrows() {
        CreateReleaseRequest req = new CreateReleaseRequest("v4.0.0", "Notes", false, "v4.0.0");

        when(releaseRepository.findByVersionLabel("v4.0.0")).thenReturn(Optional.of(new PlatformRelease()));

        assertThatThrownBy(() -> controller.createRelease(req, authFor(PlatformRole.RELEASE_MANAGER)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void ACT_RELEASE_MANAGE_ALLOW_transition() {
        UUID releaseId = UUID.randomUUID();
        PlatformRelease release = PlatformRelease.builder()
                .releaseId(releaseId)
                .versionLabel("v4.0.0")
                .status("UPLOADED")
                .build();

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(release));
        when(releaseRepository.save(any(PlatformRelease.class))).thenAnswer(inv -> inv.getArgument(0));

        ReleaseTransitionRequest req = new ReleaseTransitionRequest("DEPLOYED");
        ResponseEntity<ReleaseResponse> res = controller.transitionRelease(releaseId, req, authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().status()).isEqualTo("DEPLOYED");
        assertThat(res.getBody().deployedAt()).isNotNull();
    }

    @Test
    void ACT_RELEASE_MANAGE_DENY_unauthorizedRole() {
        CreateReleaseRequest req = new CreateReleaseRequest("v4.0.1", "Hotfix", true, "v4.0.1");

        assertThatThrownBy(() -> controller.createRelease(req, authFor(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }
}
