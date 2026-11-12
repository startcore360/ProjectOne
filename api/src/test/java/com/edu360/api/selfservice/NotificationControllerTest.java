package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.selfservice.dto.NotificationSummaryDto;

class NotificationControllerTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final TenantPrincipal principal =
            new TenantPrincipal(tenantId, userId, UserType.STAFF, "Staff", false);
    private final Authentication authentication = mock(Authentication.class);
    private NotificationService service;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        service = mock(NotificationService.class);
        controller = new NotificationController(service);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    @Test
    void listUsesAuthenticatedRecipientAndLimit() {
        when(service.list(tenantId, userId, 20)).thenReturn(List.of());

        var response = controller.list(tenantId, 20, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).list(tenantId, userId, 20);
    }

    @Test
    void summaryUsesAuthenticatedRecipient() {
        when(service.summary(tenantId, userId)).thenReturn(new NotificationSummaryDto(3));

        var response = controller.summary(tenantId, authentication);

        assertThat(response.getBody().unreadCount()).isEqualTo(3);
        verify(service).summary(tenantId, userId);
    }

    @Test
    void tenantMismatchDoesNotReachNotificationService() {
        assertThatThrownBy(() -> controller.list(UUID.randomUUID(), 50, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(service, never()).list(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void missingAuthenticationIsRejected() {
        assertThatThrownBy(() -> controller.markAllRead(tenantId, null))
                .isInstanceOf(UnauthorizedException.class);
    }
}
