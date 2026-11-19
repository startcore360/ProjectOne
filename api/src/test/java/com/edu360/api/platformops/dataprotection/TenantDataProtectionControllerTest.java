package com.edu360.api.platformops.dataprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.DeletionRequestResponse;
import com.edu360.api.platformops.dataprotection.dto.DataProtectionDtos.ExportRequestResponse;
import com.edu360.api.rbac.PermissionDecision;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

class TenantDataProtectionControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private TenantDataProtectionService service;
    private PermissionEvaluatorService evaluator;
    private TenantDataProtectionController controller;

    @BeforeEach
    void setUp() {
        service = mock(TenantDataProtectionService.class);
        evaluator = mock(PermissionEvaluatorService.class);
        controller = new TenantDataProtectionController(service, evaluator);
    }

    private Authentication tenantAuth(UUID tenantId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, USER_ID, UserType.STAFF, "School Admin", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private void allow(String action) {
        when(evaluator.can(any(RbacPrincipal.class), eq(action), eq(RbacScope.tenant()), eq(RbacTarget.NONE)))
                .thenReturn(PermissionDecision.allow());
    }

    @Test
    void schoolAdminWithExportActionCanRequestExportWithoutRoleNameChecks() {
        allow("TENANT_EXPORT_REQUEST");
        ExportRequestResponse response = new ExportRequestResponse(UUID.randomUUID(), TENANT_ID,
                "School", "TENANT_ADMIN", "REQUESTED", null, null, java.time.Instant.now(), 1);
        when(service.requestExport(TENANT_ID, USER_ID)).thenReturn(response);

        ResponseEntity<ExportRequestResponse> result = controller.requestExport(TENANT_ID, tenantAuth(TENANT_ID));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
        verify(evaluator).authorize(new RbacPrincipal(TENANT_ID, USER_ID), "TENANT_EXPORT_REQUEST",
                RbacScope.tenant(), RbacTarget.NONE);
        verify(service).requestExport(TENANT_ID, USER_ID);
    }

    @Test
    void deletionRequestChecksDeletionActionOnly() {
        allow("TENANT_DELETION_REQUEST");
        DeletionRequestResponse response = new DeletionRequestResponse(UUID.randomUUID(), TENANT_ID,
                "School", "TENANT_ADMIN", "REQUESTED", null, null, null,
                java.time.Instant.now(), null, null, null, null, null, 0);
        when(service.requestDeletion(TENANT_ID, USER_ID)).thenReturn(response);

        ResponseEntity<DeletionRequestResponse> result =
                controller.requestDeletion(TENANT_ID, tenantAuth(TENANT_ID));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(evaluator).authorize(new RbacPrincipal(TENANT_ID, USER_ID), "TENANT_DELETION_REQUEST",
                RbacScope.tenant(), RbacTarget.NONE);
    }

    @Test
    void historyIsAllowedWhenUserHoldsOnlyDeletionAction() {
        allow("TENANT_DELETION_REQUEST");
        when(evaluator.can(any(RbacPrincipal.class), eq("TENANT_EXPORT_REQUEST"),
                eq(RbacScope.tenant()), eq(RbacTarget.NONE))).thenReturn(PermissionDecision.deny("ACTION_NOT_GRANTED", "No export action."));
        when(service.listExports(TENANT_ID)).thenReturn(List.of());

        ResponseEntity<List<ExportRequestResponse>> result =
                controller.exportHistory(TENANT_ID, tenantAuth(TENANT_ID));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).listExports(TENANT_ID);
    }

    @Test
    void historyIsAllowedWhenUserHoldsOnlyExportAction() {
        allow("TENANT_EXPORT_REQUEST");
        when(service.listDeletions(TENANT_ID)).thenReturn(List.of());

        ResponseEntity<List<DeletionRequestResponse>> result =
                controller.deletionHistory(TENANT_ID, tenantAuth(TENANT_ID));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).listDeletions(TENANT_ID);
    }

    @Test
    void historyIsDeniedWhenNeitherActionIsHeld() {
        when(evaluator.can(any(RbacPrincipal.class), any(), eq(RbacScope.tenant()), eq(RbacTarget.NONE)))
                .thenReturn(PermissionDecision.deny("ACTION_NOT_GRANTED", "Not granted."));
        org.mockito.Mockito.doThrow(new UnauthorizedException("Not granted."))
                .when(evaluator).authorize(any(), eq("TENANT_EXPORT_REQUEST"), any(), any());

        assertThatThrownBy(() -> controller.exportHistory(TENANT_ID, tenantAuth(TENANT_ID)))
                .isInstanceOf(UnauthorizedException.class);
        verify(service, never()).listExports(any());
    }

    @Test
    void urlTenantMismatchIsForbiddenBeforePermissionOrServiceAccess() {
        assertThatThrownBy(() -> controller.exportHistory(TENANT_ID, tenantAuth(UUID.randomUUID())))
                .isInstanceOf(UnauthorizedException.class);
        verify(evaluator, never()).can(any(), any(), any(), any());
        verify(service, never()).listExports(any());
    }

    @Test
    void platformPrincipalCannotUseTenantEndpoints() {
        PlatformPrincipal platform = new PlatformPrincipal(USER_ID, PlatformRole.PRODUCT_OWNER,
                "owner@example.test", false);
        Authentication auth = new UsernamePasswordAuthenticationToken(platform, null, List.of());

        assertThatThrownBy(() -> controller.requestDeletion(TENANT_ID, auth))
                .isInstanceOf(UnauthorizedException.class);
        verify(service, never()).requestDeletion(any(), any());
    }

    @Test
    void anotherTenantsExportIdIsNotFoundAndDoesNotExposeExistence() {
        allow("TENANT_EXPORT_REQUEST");
        UUID foreignRequestId = UUID.randomUUID();
        when(service.openDownload(TENANT_ID, foreignRequestId))
                .thenThrow(new ResourceNotFoundException("Export request not found."));

        assertThatThrownBy(() -> controller.downloadExport(TENANT_ID, foreignRequestId, tenantAuth(TENANT_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(service).openDownload(TENANT_ID, foreignRequestId);
    }

    @Test
    void downloadDelegatesOnlyAuthenticatedTenantAndReturnsZip() {
        allow("TENANT_EXPORT_REQUEST");
        when(service.openDownload(TENANT_ID, UUID.randomUUID())).thenReturn(new ByteArrayInputStream(new byte[0]));
        // Use a stable id so the mock verifies the exact tenant/id pair.
        UUID requestId = UUID.randomUUID();
        when(service.openDownload(TENANT_ID, requestId)).thenReturn(new ByteArrayInputStream(new byte[0]));

        ResponseEntity<?> result = controller.downloadExport(TENANT_ID, requestId, tenantAuth(TENANT_ID));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.parseMediaType("application/zip"));
        verify(service).openDownload(TENANT_ID, requestId);
    }
}
