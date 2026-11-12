package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;

class NotificationServiceTest {
    @Test
    void listUsesTenantRecipientAndRequestedLimit() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        NotificationService service = new NotificationService(repository);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findDeliverable(eq(tenantId), eq(userId), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.list(tenantId, userId, 25)).isEmpty();
        verify(repository).findDeliverable(eq(tenantId), eq(userId), any(Instant.class),
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageSize() == 25));
    }

    @Test
    void rejectsInvalidListLimit() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        NotificationService service = new NotificationService(repository);

        assertThatThrownBy(() -> service.list(UUID.randomUUID(), UUID.randomUUID(), 101))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("limit must be between");
        verify(repository, never()).findDeliverable(any(), any(), any(), any());
    }

    @Test
    void summaryCountsOnlyUnreadDeliverableNotificationsForRecipient() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        NotificationService service = new NotificationService(repository);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.countUnreadDeliverable(eq(tenantId), eq(userId), any(Instant.class))).thenReturn(4L);

        assertThat(service.summary(tenantId, userId).unreadCount()).isEqualTo(4L);
        verify(repository).countUnreadDeliverable(eq(tenantId), eq(userId), any(Instant.class));
    }

    @Test
    void markReadUpdatesOnlyNotificationOwnedByRecipient() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        NotificationService service = new NotificationService(repository);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UserNotification row = mock(UserNotification.class);
        when(row.getReadAt()).thenReturn(null);
        when(repository.findByIdTenantIdAndIdNotificationIdAndRecipientUserId(tenantId, notificationId, userId))
                .thenReturn(Optional.of(row));

        service.markRead(tenantId, userId, notificationId);

        verify(row).setReadAt(any(Instant.class));
        verify(row).setUpdatedBy(userId);
        verify(repository).save(row);
    }

    @Test
    void markReadReturnsNotFoundForAnotherUsersNotification() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        NotificationService service = new NotificationService(repository);
        when(repository.findByIdTenantIdAndIdNotificationIdAndRecipientUserId(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void markAllReadDelegatesTenantAndRecipientScopedUpdate() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        NotificationService service = new NotificationService(repository);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.markAllDeliveredRead(eq(tenantId), eq(userId), any(Instant.class))).thenReturn(2);

        assertThat(service.markAllRead(tenantId, userId)).isEqualTo(2);
        verify(repository).markAllDeliveredRead(eq(tenantId), eq(userId), any(Instant.class));
    }
}
