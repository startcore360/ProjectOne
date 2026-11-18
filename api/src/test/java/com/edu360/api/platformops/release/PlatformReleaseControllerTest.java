package com.edu360.api.platformops.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import com.edu360.api.platformops.release.dto.ScheduleReleaseRequest;

class PlatformReleaseControllerTest {

    private PlatformReleaseRepository releaseRepository;
    private AuditLogService auditLogService;
    private PlatformPermissionService permissionService;
    private PlatformReleaseService releaseService;
    private PlatformReleaseNotificationService releaseNotificationService;
    private PlatformReleaseHealthService releaseHealthService;
    private PlatformSchemaReleaseRepository schemaReleaseRepository;
    private PlatformReleaseController controller;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        releaseRepository = mock(PlatformReleaseRepository.class);
        auditLogService = mock(AuditLogService.class);
        permissionService = com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());
        releaseNotificationService = mock(PlatformReleaseNotificationService.class);
        releaseHealthService = mock(PlatformReleaseHealthService.class);
        schemaReleaseRepository = mock(PlatformSchemaReleaseRepository.class);
        releaseService = new PlatformReleaseService(releaseRepository,
                org.mockito.Mockito.mock(com.edu360.api.platformops.role.PlatformAuditWriter.class),
                releaseNotificationService, schemaReleaseRepository);
        controller = new PlatformReleaseController(releaseService, permissionService);
        controller.setHealthService(releaseHealthService);
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
                .status("STAGING_TESTED")
                .scheduledAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(release));
        when(releaseRepository.save(any(PlatformRelease.class))).thenAnswer(inv -> inv.getArgument(0));

        ReleaseTransitionRequest req = new ReleaseTransitionRequest("DEPLOYED", null);
        ResponseEntity<ReleaseResponse> res = controller.transitionRelease(releaseId, req, authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().status()).isEqualTo("DEPLOYED");
        assertThat(res.getBody().deployedAt()).isNotNull();
        verify(releaseNotificationService).notifyTenantAdmins(release);
    }

    @Test
    void releaseHealthIsAvailableToReleaseManager() {
        UUID releaseId = UUID.randomUUID();
        Instant deployedAt = Instant.now().minus(2, ChronoUnit.DAYS);
        ReleaseHealthResponse.Window before = new ReleaseHealthResponse.Window(
                deployedAt.minus(24, ChronoUnit.HOURS), deployedAt, 2L, null, true);
        ReleaseHealthResponse.Window after = new ReleaseHealthResponse.Window(
                deployedAt, deployedAt.plus(24, ChronoUnit.HOURS), 1L, null, true);
        ReleaseHealthResponse expected = new ReleaseHealthResponse(
                releaseId, "v4.3.0", deployedAt, before, after);
        when(releaseHealthService.getHealth(releaseId)).thenReturn(expected);

        ResponseEntity<ReleaseHealthResponse> response =
                controller.getReleaseHealth(releaseId, authFor(PlatformRole.RELEASE_MANAGER));

        assertThat(response.getBody()).isEqualTo(expected);
        verify(releaseHealthService).getHealth(releaseId);
    }

    @Test
    void scheduleRelease_persistsTimeAndLeavesStatusStagingTested() {
        UUID releaseId = UUID.randomUUID();
        PlatformRelease release = PlatformRelease.builder()
                .releaseId(releaseId)
                .status("STAGING_TESTED")
                .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(release));
        when(releaseRepository.save(any(PlatformRelease.class))).thenAnswer(inv -> inv.getArgument(0));
        Instant scheduledAt = Instant.now().plus(2, ChronoUnit.HOURS);

        ResponseEntity<ReleaseResponse> response = controller.scheduleRelease(
                releaseId, new ScheduleReleaseRequest(scheduledAt), authFor(PlatformRole.RELEASE_MANAGER));

        assertThat(response.getBody().scheduledAt()).isEqualTo(scheduledAt);
        assertThat(response.getBody().status()).isEqualTo("STAGING_TESTED");
    }

    @Test
    void scheduleRelease_returnsConflictForNonStagingTestedRelease() {
        UUID releaseId = UUID.randomUUID();
        PlatformRelease release = PlatformRelease.builder()
                .releaseId(releaseId)
                .status("UPLOADED")
                .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(release));

        assertThatThrownBy(() -> controller.scheduleRelease(
                releaseId,
                new ScheduleReleaseRequest(Instant.now().plus(1, ChronoUnit.HOURS)),
                authFor(PlatformRole.RELEASE_MANAGER)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("NOT_STAGING_TESTED");
    }

    @Test
    void deployReturnsConflictForNonStagingTestedReleaseEvenForProductOwner() {
        UUID releaseId = UUID.randomUUID();
        PlatformRelease release = PlatformRelease.builder()
                .releaseId(releaseId)
                .status("UPLOADED")
                .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(release));

        assertThatThrownBy(() -> controller.transitionRelease(
                releaseId,
                new ReleaseTransitionRequest("DEPLOYED", "Emergency"),
                authFor(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("NOT_STAGING_TESTED");
    }

    @Test
    void productOwnerCanDeployEarlyWithReason() {
        UUID releaseId = UUID.randomUUID();
        PlatformRelease release = PlatformRelease.builder()
                .releaseId(releaseId)
                .status("STAGING_TESTED")
                .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(release));
        when(releaseRepository.save(any(PlatformRelease.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<ReleaseResponse> response = controller.transitionRelease(
                releaseId,
                new ReleaseTransitionRequest("DEPLOYED", "Emergency correction"),
                authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(response.getBody().status()).isEqualTo("DEPLOYED");
    }

    @Test
    void releaseManagerCannotDeployWithoutDueSchedule() {
        UUID releaseId = UUID.randomUUID();
        PlatformRelease release = PlatformRelease.builder()
                .releaseId(releaseId)
                .status("STAGING_TESTED")
                .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(release));

        assertThatThrownBy(() -> controller.transitionRelease(
                releaseId,
                new ReleaseTransitionRequest("DEPLOYED", null),
                authFor(PlatformRole.RELEASE_MANAGER)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void updateReleaseRecordsHighestSchemaVersionAndOrderedAppliedMigrationChecksums() {
        UUID releaseId = UUID.randomUUID();
        PlatformRelease release = PlatformRelease.builder()
                .releaseId(releaseId)
                .versionLabel("v4.4.0")
                .releaseNotes("Old notes")
                .status("UPLOADED")
                .schemaVersion("V41__platform_updates")
                .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(release));
        when(releaseRepository.save(any(PlatformRelease.class))).thenAnswer(inv -> inv.getArgument(0));
        when(schemaReleaseRepository.findByReleaseIdAndSchemaVersion(releaseId, "V42__platform_updates"))
                .thenReturn(Optional.empty());
        when(schemaReleaseRepository.findAppliedMigrationFilesByReleaseId(releaseId))
                .thenReturn(List.of("42_v41_change_a.sql:" + "a".repeat(64),
                        "43_v41_change_b.sql:" + "b".repeat(64)));

        ResponseEntity<ReleaseResponse> response = controller.updateRelease(
                releaseId,
                new com.edu360.api.platformops.release.dto.UpdateReleaseRequest(
                        "Updated notes", false, "V42__platform_updates"),
                authFor(PlatformRole.RELEASE_MANAGER));

        assertThat(response.getBody().schemaVersion()).isEqualTo("V42__platform_updates");
        org.mockito.ArgumentCaptor<PlatformSchemaRelease> saved =
                org.mockito.ArgumentCaptor.forClass(PlatformSchemaRelease.class);
        verify(schemaReleaseRepository).save(saved.capture());
        assertThat(saved.getValue().getReleaseId()).isEqualTo(releaseId);
        assertThat(saved.getValue().getSchemaVersion()).isEqualTo("V42__platform_updates");
        assertThat(saved.getValue().getMigrationFiles()).containsExactly(
                "42_v41_change_a.sql:" + "a".repeat(64),
                "43_v41_change_b.sql:" + "b".repeat(64));
    }

    @Test
    void ACT_RELEASE_MANAGE_DENY_unauthorizedRole() {
        CreateReleaseRequest req = new CreateReleaseRequest("v4.0.1", "Hotfix", true, "v4.0.1");

        assertThatThrownBy(() -> controller.createRelease(req, authFor(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }
}
