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
}
