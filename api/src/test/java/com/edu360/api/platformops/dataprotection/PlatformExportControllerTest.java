package com.edu360.api.platformops.dataprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformAccessLookup;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.PlatformTestSupport;

class PlatformExportControllerTest {

    private final ExportRequestLifecycleService lifecycle = mock(ExportRequestLifecycleService.class);
    private final ExportRequestRepository exports = mock(ExportRequestRepository.class);
    private final DeletionRequestRepository deletions = mock(DeletionRequestRepository.class);
    private final RetentionChangeRequestRepository retention = mock(RetentionChangeRequestRepository.class);
    private final PlatformPermissionService permissions = PlatformTestSupport.permissions(PlatformAccessLookup.standardRolesOnly());
    private final PlatformExportController controller =
            new PlatformExportController(permissions, lifecycle, exports, deletions, retention);

    private static Authentication auth(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), role,
                role.name().toLowerCase() + "@platform.edu360.local", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void pendingCountAddsRequestedExportsPendingDeletionsAndRequestedRetentionChanges() {
        when(exports.countByStatus("REQUESTED")).thenReturn(2L);
        when(deletions.countByStatus("PENDING_DPO")).thenReturn(3L);
        when(retention.countByStatus("REQUESTED")).thenReturn(4L);

        ResponseEntity<Map<String, Long>> response = controller.pendingCount(auth(PlatformRole.PRODUCT_OWNER));

        assertThat(response.getBody()).containsExactly(Map.entry("count", 9L));
    }

    @Test
    void pendingCountIsRefusedToRolesWithoutARequestAction() {
        assertThatThrownBy(() -> controller.pendingCount(auth(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(exports, deletions, retention);
    }

    @Test
    void downloadIsRefusedToRolesWithoutAnExportAction() {
        assertThatThrownBy(() -> controller.download(UUID.randomUUID(), auth(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(lifecycle);
    }

    @Test
    void downloadIsAllowedForTheDpo() {
        UUID id = UUID.randomUUID();
        when(lifecycle.openDownload(any(), any())).thenReturn(new java.io.ByteArrayInputStream(new byte[] {1}));

        assertThat(controller.download(id, auth(PlatformRole.DATA_PROTECTION_OFFICER)).getStatusCode().is2xxSuccessful())
                .isTrue();
    }
}
