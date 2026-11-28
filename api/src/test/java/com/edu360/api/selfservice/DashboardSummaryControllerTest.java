package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserAccountService;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.SectionRepository;

class DashboardSummaryControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private Authentication authenticatedTenantUser(UUID tenantId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, USER_ID, UserType.STAFF, "School Admin", false);
        return new UsernamePasswordAuthenticationToken(principal, "n/a", java.util.Collections.emptyList());
    }

    @Test
    void summary_returnsActiveUserAndSectionCounts() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        SectionRepository sectionRepository = mock(SectionRepository.class);
        DashboardSummaryController controller = new DashboardSummaryController(userAccountService, sectionRepository);

        when(userAccountService.currentUserLimit(TENANT_ID)).thenReturn(new UserAccountService.UserLimit(50, 12L, 24));
        when(sectionRepository.countByTenantIdAndStatus(TENANT_ID, "ACTIVE")).thenReturn(7L);

        ResponseEntity<DashboardSummaryController.DashboardSummary> response =
                controller.summary(TENANT_ID, authenticatedTenantUser(TENANT_ID));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().activeUsers()).isEqualTo(12L);
        assertThat(response.getBody().activeSections()).isEqualTo(7L);
    }

    @Test
    void summary_crossTenantRequestIsRejected() {
        DashboardSummaryController controller =
                new DashboardSummaryController(mock(UserAccountService.class), mock(SectionRepository.class));
        UUID foreignTenantId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.summary(foreignTenantId, authenticatedTenantUser(TENANT_ID)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void summary_unauthenticatedRequestIsRejected() {
        DashboardSummaryController controller =
                new DashboardSummaryController(mock(UserAccountService.class), mock(SectionRepository.class));

        assertThatThrownBy(() -> controller.summary(TENANT_ID, null))
                .isInstanceOf(UnauthorizedException.class);
    }
}
