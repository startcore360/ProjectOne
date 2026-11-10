package com.edu360.api.platformops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.entitlement.PlatformFeatureToggleController;
import com.edu360.api.platformops.report.PlatformReportController;

class PlatformSecurityReportControllerTest {

    private PlatformPermissionService permissionService;
    private AuditLogService auditLogService;
    private PlatformSecurityPolicyController securityPolicyController;
    private PlatformFeatureToggleController featureToggleController;
    private PlatformReportController reportController;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        permissionService = com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());
        auditLogService = mock(AuditLogService.class);
        securityPolicyController = new PlatformSecurityPolicyController(permissionService, null, null, org.mockito.Mockito.mock(com.edu360.api.platformops.role.PlatformAuditWriter.class), null);
        featureToggleController = new PlatformFeatureToggleController(permissionService, null, null, org.mockito.Mockito.mock(com.edu360.api.platformops.role.PlatformAuditWriter.class));
        reportController = new PlatformReportController(permissionService);
    }

    private Authentication authFor(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(USER_ID, role, "admin@edu360.local", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void ACT_SECURITY_POLICY_SET_ALLOW_productOwner() {
        var req = new PlatformSecurityPolicyController.UpdateSecurityPolicyRequest(
                14, true, true, 3, 30, 20, true, 180
        );

        ResponseEntity<PlatformSecurityPolicyController.SecurityPolicyResponse> res =
                securityPolicyController.updatePolicy(req, authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().minPasswordLength()).isEqualTo(14);
        assertThat(res.getBody().defaultBackupRetentionDays()).isEqualTo(180);
    }

    @Test
    void ACT_SECURITY_POLICY_SET_DENY_platformAdmin() {
        var req = new PlatformSecurityPolicyController.UpdateSecurityPolicyRequest(
                14, true, true, 3, 30, 20, true, 180
        );

        // Platform Admin is denied setting security policy (Product Owner only per prompt specification)
        assertThatThrownBy(() -> securityPolicyController.updatePolicy(req, authFor(PlatformRole.PLATFORM_ADMIN)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void H15_SECURITY_POLICY_SET_minPasswordLengthLessThan8_rejected() {
        var req = new PlatformSecurityPolicyController.UpdateSecurityPolicyRequest(
                0, true, true, 3, 30, 20, true, 180
        );

        assertThatThrownBy(() -> securityPolicyController.updatePolicy(req, authFor(PlatformRole.PRODUCT_OWNER)))
                .isInstanceOf(com.edu360.api.exception.BusinessRuleViolationException.class)
                .hasMessageContaining("Minimum password length must be at least 8");
    }

    @Test
    void ACT_FEATURE_TOGGLE_ALLOW() {
        UUID planId = UUID.randomUUID();
        var req = new PlatformFeatureToggleController.EntitlementsUpdateRequest(
                Map.of("HOSTEL_MANAGEMENT", true, "TRANSPORT_TRACKING", false)
        );

        ResponseEntity<PlatformFeatureToggleController.EntitlementsResponse> res =
                featureToggleController.updatePlanEntitlements(planId, req, authFor(PlatformRole.PLATFORM_ADMIN));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().features().get("HOSTEL_MANAGEMENT")).isTrue();
    }

    @Test
    void ACT_FEATURE_TOGGLE_DENY_billingManager() {
        UUID planId = UUID.randomUUID();
        var req = new PlatformFeatureToggleController.EntitlementsUpdateRequest(
                Map.of("HOSTEL_MANAGEMENT", true)
        );

        assertThatThrownBy(() -> featureToggleController.updatePlanEntitlements(planId, req, authFor(PlatformRole.BILLING_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void ACT_PLATFORM_REPORTS_VIEW_ALLOW_billingManagerGetsFinancials() {
        ResponseEntity<PlatformReportController.PlatformReportResponse> res =
                reportController.getReport("REVENUE_SUMMARY", authFor(PlatformRole.BILLING_MANAGER));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().metrics()).containsKey("totalRevenueYtd");
    }

    @Test
    void ACT_PLATFORM_REPORTS_VIEW_ALLOW_supportManagerGetsTicketMetrics() {
        ResponseEntity<PlatformReportController.PlatformReportResponse> res =
                reportController.getReport("SUPPORT_SUMMARY", authFor(PlatformRole.SUPPORT_MANAGER));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().metrics()).containsKey("openTicketsCount");
    }
}
