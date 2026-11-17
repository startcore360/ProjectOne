package com.edu360.api.platformops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlatformNotificationReadServiceTest {

    @Test
    void listsOnlyTheRequestedRecipientAndMapsReadState() {
        PlatformNotificationRepository repository = mock(PlatformNotificationRepository.class);
        UUID recipient = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-17T10:00:00Z");
        PlatformNotification notification = PlatformNotification.builder()
                .platformNotificationId(UUID.randomUUID())
                .recipientPlatformUserId(recipient)
                .triggerCode("TENANT_CREATED")
                .title("Tenant created")
                .body("A tenant was created.")
                .priorityClass("NORMAL")
                .createdAt(createdAt)
                .createdBy(recipient)
                .updatedAt(createdAt)
                .updatedBy(recipient)
                .rowVersion(1)
                .build();
        when(repository.findTop50ByRecipientPlatformUserIdOrderByCreatedAtDesc(recipient))
                .thenReturn(List.of(notification));

        PlatformNotificationReadService service = new PlatformNotificationReadService(repository);
        List<PlatformNotificationResponse> result = service.list(recipient);

        assertEquals(1, result.size());
        assertEquals(notification.getPlatformNotificationId(), result.get(0).notificationId());
        assertEquals(null, result.get(0).readAt());
        verify(repository).findTop50ByRecipientPlatformUserIdOrderByCreatedAtDesc(recipient);
    }

    @Test
    void marksOneNotificationUsingBothNotificationAndRecipientIds() {
        PlatformNotificationRepository repository = mock(PlatformNotificationRepository.class);
        PlatformNotificationReadService service = new PlatformNotificationReadService(repository);
        UUID recipient = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        service.markRead(recipient, notificationId);

        verify(repository).markReadForRecipient(eq(notificationId), eq(recipient), any(Instant.class));
    }

    @Test
    void marksAllNotificationsForOnlyTheRequestedRecipient() {
        PlatformNotificationRepository repository = mock(PlatformNotificationRepository.class);
        PlatformNotificationReadService service = new PlatformNotificationReadService(repository);
        UUID recipient = UUID.randomUUID();

        service.markAllRead(recipient);

        ArgumentCaptor<Instant> timestamp = ArgumentCaptor.forClass(Instant.class);
        verify(repository).markAllReadForRecipient(eq(recipient), timestamp.capture());
    }
}
