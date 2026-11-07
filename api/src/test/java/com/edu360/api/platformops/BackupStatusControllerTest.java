package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.dto.BackupStatusResponse;

@ExtendWith(MockitoExtension.class)
class BackupStatusControllerTest {

    @Mock private TenantBackupStatusRepository tenantBackupStatusRepository;

    private final PlatformPermissionService platformPermissionService = new PlatformPermissionService(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());

    private BackupStatusController controller() {
        return new BackupStatusController(platformPermissionService, tenantBackupStatusRepository);
    }

    private Authentication authFor(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), role, "po@edu360.test", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void backupStatus_reportsEmptyListAndFlagsNotWired_whenNoRowsRecorded() {
        when(tenantBackupStatusRepository.findAllByOrderByTenantId()).thenReturn(List.of());

        ResponseEntity<BackupStatusResponse> response = controller().backupStatus(authFor(PlatformRole.PLATFORM_ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().wiredToRealBackupSystem()).isFalse();
        assertThat(response.getBody().tenants()).isEmpty();
        assertThat(response.getBody().note()).isNotBlank();
    }

    @Test
    void backupStatus_deniesNonPlatformRole() {
        assertThatThrownBy(() -> controller().backupStatus(authFor(PlatformRole.RELEASE_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }
}
