package com.edu360.api.platformops.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.TenantUserLimitController;
import com.edu360.api.identity.UserAccountService;
import com.edu360.api.identity.UserType;
import com.edu360.api.platformops.support.dto.CreateSupportTicketRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;

/** E1-S5 (WF-01): the user-limit reading and "Request upgrade" as a BILLING ticket. */
@ExtendWith(MockitoExtension.class)
class TenantUserLimitAndUpgradeTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock private PlatformSupportService supportService;
    @Mock private PermissionEvaluatorService permissions;
    @Mock private UserAccountService accounts;

    private Authentication signedIn(UUID tenantId) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new TenantPrincipal(tenantId, USER, UserType.STAFF, "Asha Admin", false));
        return auth;
    }

    @Test
    void theUserLimitIsShownToTheTenantsOwnUsersOnly() {
        TenantUserLimitController controller = new TenantUserLimitController(accounts);
        when(accounts.currentUserLimit(TENANT)).thenReturn(new UserAccountService.UserLimit(1000, 900, 90));

        assertThat(controller.userLimit(TENANT, signedIn(TENANT)).getBody().percent()).isEqualTo(90);
        assertThatThrownBy(() -> controller.userLimit(TENANT, signedIn(UUID.randomUUID())))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void requestUpgradeRaisesABillingTicketWithTheCurrentUsage() {
        TenantUpgradeRequestController controller = new TenantUpgradeRequestController(supportService, permissions, accounts);
        when(accounts.currentUserLimit(TENANT)).thenReturn(new UserAccountService.UserLimit(1000, 1000, 100));

        controller.requestUpgrade(TENANT, new TenantUpgradeRequestController.UpgradeRequest("We are adding a new campus"),
                signedIn(TENANT));

        ArgumentCaptor<CreateSupportTicketRequest> ticket = ArgumentCaptor.forClass(CreateSupportTicketRequest.class);
        verify(supportService).raiseTicket(eq(TENANT), ticket.capture(), eq(USER), eq("Asha Admin"));
        assertThat(ticket.getValue().category()).isEqualTo("BILLING");
        assertThat(ticket.getValue().subject()).isEqualTo("Upgrade request");
        assertThat(ticket.getValue().description()).contains("1000 of 1000 (100%)").contains("new campus");
    }

    @Test
    void withoutTheTicketPermissionNoUpgradeRequestIsRaised() {
        TenantUpgradeRequestController controller = new TenantUpgradeRequestController(supportService, permissions, accounts);
        doThrow(new UnauthorizedException("SUPPORT_TICKET_RAISE not held")).when(permissions)
                .authorize(any(), eq("SUPPORT_TICKET_RAISE"), any(), any());

        assertThatThrownBy(() -> controller.requestUpgrade(TENANT, null, signedIn(TENANT)))
                .isInstanceOf(UnauthorizedException.class);
        verify(supportService, never()).raiseTicket(any(), any(), any(), any());
    }
}
