package com.edu360.api.platformops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlatformNotificationServiceTest {

    @Test
    void writesNotificationForPlatformRecipient() {
        PlatformNotificationRepository repository = mock(PlatformNotificationRepository.class);
        when(repository.save(any(PlatformNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformNotificationService service = new PlatformNotificationService(repository);
        UUID recipient = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        UUID notificationId = service.notify(recipient, "EXPORT_REQUEST_DECIDED", "Export approved",
                "Your export request was approved.", "EXPORT_REQUEST", UUID.randomUUID(), null, actor);

        assertNotNull(notificationId);
        ArgumentCaptor<PlatformNotification> captor = ArgumentCaptor.forClass(PlatformNotification.class);
        verify(repository).save(captor.capture());
        PlatformNotification saved = captor.getValue();
        assertEquals(notificationId, saved.getPlatformNotificationId());
        assertEquals(recipient, saved.getRecipientPlatformUserId());
        assertEquals(actor, saved.getCreatedBy());
        assertEquals("NORMAL", saved.getPriorityClass());
        assertEquals(null, saved.getReadAt());
    }

    @Test
    void requiresRecipientAndActor() {
        PlatformNotificationService service = new PlatformNotificationService(mock(PlatformNotificationRepository.class));
        assertThrows(IllegalArgumentException.class,
                () -> service.notify(null, "EVENT", "Title", null, null, null, null, UUID.randomUUID()));
    }
}
