package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.dto.AuditLogPageResponse;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@ExtendWith(MockitoExtension.class)
class PlatformAuditLogControllerTest {
    @Mock private PlatformAuditOutboxEventRepository auditOutboxEventRepository;
    @Mock private PlatformAuditWriter platformAuditWriter;

    private final PlatformPermissionService platformPermissionService =
            PlatformTestSupport.permissions(PlatformAccessLookup.standardRolesOnly());

    private PlatformAuditLogController controller() {
        return new PlatformAuditLogController(platformPermissionService, auditOutboxEventRepository, platformAuditWriter);
    }

    private Authentication authFor(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), role, "po@edu360.test", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private PlatformAuditOutboxEvent sampleEvent() {
        return PlatformAuditOutboxEvent.builder()
                .auditOutboxId(UUID.randomUUID())
                .occurredAt(Instant.parse("2026-11-16T12:00:00Z"))
                .actorKind("PLATFORM_USER")
                .actorUserId(UUID.randomUUID())
                .operation("VIEW_SENSITIVE")
                .actionCode("TENANT_VIEW")
                .entityType("TENANT")
                .entityId(UUID.randomUUID())
                .outcome("SUCCESS")
                .correlationId("corr-1")
                .build();
    }

    @Test
    void listAuditLogs_allowedForPlatformAdmin_returnsFilteredPage() {
        PlatformAuditOutboxEvent event = sampleEvent();
        Page<PlatformAuditOutboxEvent> page = new PageImpl<>(List.of(event), PageRequest.of(0, 50), 1);
        when(auditOutboxEventRepository.findFiltered(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        ResponseEntity<AuditLogPageResponse> response = controller()
                .listAuditLogs(null, null, "TENANT_VIEW", "SUCCESS", null, null, 0, 50,
                        authFor(PlatformRole.PLATFORM_ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().totalElements()).isEqualTo(1);
    }

    @Test
    void listAuditLogs_deniesNonPlatformRole() {
        assertThatThrownBy(() -> controller().listAuditLogs(null, null, null, null, null, null, 0, 50,
                authFor(PlatformRole.BILLING_MANAGER))).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void listAuditLogs_deniesNonPlatformPrincipal() {
        Authentication nonPlatformAuth = new UsernamePasswordAuthenticationToken("not-a-platform-principal", null, List.of());
        assertThatThrownBy(() -> controller().listAuditLogs(null, null, null, null, null, null, 0, 50, nonPlatformAuth))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void listAuditLogs_clampsOversizedPageSize() {
        Page<PlatformAuditOutboxEvent> page = new PageImpl<>(List.of(), PageRequest.of(0, 200), 0);
        when(auditOutboxEventRepository.findFiltered(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);
        controller().listAuditLogs(null, null, null, null, null, null, 0, 999, authFor(PlatformRole.PRODUCT_OWNER));
        verify(auditOutboxEventRepository).findFiltered(any(), any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(p -> p.getPageSize() == 200));
    }

    @Test
    void exportAuditLogs_streamsCsvAndWritesExportAuditEvent() throws Exception {
        PlatformAuditOutboxEvent event = sampleEvent();
        when(auditOutboxEventRepository.countFiltered(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(1L);
        when(auditOutboxEventRepository.findFiltered(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 500), 1));

        ResponseEntity<StreamingResponseBody> response = controller().exportAuditLogs(
                null, null, null, null, null, null, authFor(PlatformRole.PRODUCT_OWNER));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("platform-audit-logs.csv");
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("TENANT_VIEW");
        verify(platformAuditWriter).record(any(PlatformPrincipal.class), isNull(), any(String.class), any(String.class),
                any(String.class), any(String.class), any(UUID.class), any(String.class));
    }

    @Test
    void exportAuditLogs_rejectsMoreThanFiftyThousandRows() {
        when(auditOutboxEventRepository.countFiltered(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(50_001L);

        assertThatThrownBy(() -> controller().exportAuditLogs(
                null, null, null, null, null, null, authFor(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("50,000");
        verify(platformAuditWriter).record(any(PlatformPrincipal.class), isNull(),
                org.mockito.ArgumentMatchers.eq("PLATFORM_AUDIT_EXPORT_REJECTED"),
                org.mockito.ArgumentMatchers.eq("EXPORT"), org.mockito.ArgumentMatchers.eq("PLATFORM_AUDIT_EXPORT"),
                org.mockito.ArgumentMatchers.eq("AUDIT_LOG_EXPORT"), any(UUID.class),
                org.mockito.ArgumentMatchers.eq(PlatformAuditWriter.FAILED));
    }
}
