package com.edu360.api.platformops.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.PlatformAccessLookup;
import com.edu360.api.platformops.role.PlatformAuditWriter;

class RbacMappingReportControllerTest {
    private final PlatformPermissionService permissions = mock(PlatformPermissionService.class);
    private final RbacMappingReportService reports = mock(RbacMappingReportService.class);
    private final PlatformAuditWriter audit = mock(PlatformAuditWriter.class);
    private final RbacMappingReportController controller =
            new RbacMappingReportController(permissions, reports, audit);

    private Authentication auth(PlatformRole role) {
        return new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal(UUID.randomUUID(), role, "dpo@example.test", false), null);
    }

    @Test
    void reportRequiresThePlatformAction() {
        Authentication authentication = auth(PlatformRole.DATA_PROTECTION_OFFICER);
        PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
        when(reports.generate()).thenReturn(new RbacMappingReport(List.of(), List.of(), List.of()));

        assertThat(controller.report(authentication).getStatusCode().is2xxSuccessful())
                .isTrue();
        verify(permissions).authorize(principal, "RBAC_MAPPING_CHECK");
    }

    @Test
    void roleWithoutRbacMappingCheckIsRefused() {
        var guarded = new RbacMappingReportController(
                com.edu360.api.platformops.PlatformTestSupport.permissions(PlatformAccessLookup.standardRolesOnly()),
                reports, audit);
        assertThatThrownBy(() -> guarded.report(auth(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void cleanReportReturnsEmptyCollections() {
        when(reports.generate()).thenReturn(new RbacMappingReport(List.of(), List.of(), List.of()));
        var response = controller.report(auth(PlatformRole.DATA_PROTECTION_OFFICER));
        assertThat(response.getBody().mismatches()).isEmpty();
        assertThat(response.getBody().noBaseTemplate()).isEmpty();
    }

    @Test
    void nonPlatformPrincipalIsRefused() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("tenant-user", null);
        assertThatThrownBy(() -> controller.report(authentication)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void csvContainsHeaderAndBothReportGroups() throws Exception {
        var drift = new RbacMappingReportRow("TENANT", UUID.randomUUID(), "Northfield",
                "CUSTOM_A", "Custom A", "GRADE_EDIT", "BASE_TEMPLATE_DRIFT", "Extra action");
        var noBase = new RbacMappingReportRow("TENANT", UUID.randomUUID(), "Northfield",
                "CUSTOM_B", "Custom B", null, "NO_BASE_TEMPLATE", "No base recorded");
        when(reports.generate()).thenReturn(new RbacMappingReport(List.of(drift), List.of(noBase), List.of()));

        var response = controller.export(auth(PlatformRole.DATA_PROTECTION_OFFICER));
        String csv = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("scope,tenantId,tenantName,roleCode,roleName,actionCode,result,detail");
        assertThat(csv).contains("GRADE_EDIT", "BASE_TEMPLATE_DRIFT", "NO_BASE_TEMPLATE", "Custom B");
    }
}
