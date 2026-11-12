package com.edu360.api.selfservice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import com.edu360.api.live.LiveEventPublisher;

import com.edu360.api.live.LiveEventPublisher;

class InAppNotifierTest {
    @Test
    void notificationPersistenceFailureDoesNotEscapeCaller() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        doThrow(new IllegalStateException("database unavailable")).when(repository).save(any(UserNotification.class));
        InAppNotifier notifier = new InAppNotifier(repository, transactionManager);

        notifier.notify(UUID.randomUUID(), UUID.randomUUID(), null, "ASSIGNMENT_PUBLISHED",
                "Assignment published", "A new assignment is available", "ASSIGNMENT",
                UUID.randomUUID(), UUID.randomUUID(), "NORMAL", "dedupe-1");

        verify(repository).save(any(UserNotification.class));
        verify(transactionManager).rollback(status);
    }

    @Test
    void duplicateDedupeKeySkipsSecondInsert() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        UUID tenantId = UUID.randomUUID();
        when(repository.findFirstByIdTenantIdAndDedupeKey(tenantId, "same-key")).thenReturn(Optional.of(mock(UserNotification.class)));
        InAppNotifier notifier = new InAppNotifier(repository, transactionManager);

        notifier.notify(tenantId, UUID.randomUUID(), null, "TIMETABLE_PUBLISHED",
                "Timetable published", null, "TIMETABLE", UUID.randomUUID(), UUID.randomUUID(), "NORMAL", "same-key");

        verify(repository, never()).save(any(UserNotification.class));
    }
    @Test
    void publishesCreatedAndUnreadEventsOnlyAfterSuccessfulInsert() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(repository.countByIdTenantIdAndRecipientUserIdAndReadAtIsNull(any(), any())).thenReturn(3L);
        LiveEventPublisher liveEvents = mock(LiveEventPublisher.class);
        InAppNotifier notifier = new InAppNotifier(repository, transactionManager);
        notifier.setLiveEventPublisher(liveEvents);
        UUID tenantId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        notifier.notify(tenantId, recipientId, null, "ASSIGNMENT_PUBLISHED",
                "Assignment published", "A new assignment is available", "ASSIGNMENT",
                UUID.randomUUID(), UUID.randomUUID(), "NORMAL", null);

        verify(repository).save(any(UserNotification.class));
        verify(liveEvents).publishToUser(eq(tenantId), eq(recipientId), eq("NOTIFICATION_CREATED"), any());
        verify(liveEvents).publishToUser(eq(tenantId), eq(recipientId), eq("UNREAD_COUNT"),
                org.mockito.ArgumentMatchers.argThat(payload -> Long.valueOf(3L).equals(payload.get("unreadCount"))));
    }

    @Test
    void duplicateNotificationDoesNotPublishLiveEvents() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(repository.findFirstByIdTenantIdAndDedupeKey(any(), eq("duplicate-key")))
                .thenReturn(Optional.of(mock(UserNotification.class)));
        LiveEventPublisher liveEvents = mock(LiveEventPublisher.class);
        InAppNotifier notifier = new InAppNotifier(repository, transactionManager);
        notifier.setLiveEventPublisher(liveEvents);

        notifier.notify(UUID.randomUUID(), UUID.randomUUID(), null, "TIMETABLE_PUBLISHED",
                "Timetable published", null, "TIMETABLE", UUID.randomUUID(), UUID.randomUUID(),
                "NORMAL", "duplicate-key");

        verify(repository, never()).save(any(UserNotification.class));
        verifyNoInteractions(liveEvents);
    }

}
