package com.edu360.api.platformops.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformAuditOutboxEvent;
import com.edu360.api.platformops.PlatformAuditOutboxEventRepository;

class PlatformAuditWriterTest {

    @Test
    void storesOldAndNewValuesAndMasksSensitiveFieldsRecursively() {
        PlatformAuditOutboxEventRepository repository = mock(PlatformAuditOutboxEventRepository.class);
        PlatformAuditWriter writer = new PlatformAuditWriter(repository);
        PlatformPrincipal actor = new PlatformPrincipal(UUID.randomUUID(), null, "owner@example.test", false);

        writer.record(actor, null, "PLAN_UPDATED", "UPDATE", "PLAN_MANAGE", "PLAN", UUID.randomUUID(),
                PlatformAuditWriter.SUCCESS,
                Map.of(
                        "userLimit", Map.of("old", 1000, "new", 1500),
                        "password", Map.of("old", "old-secret", "new", "new-secret"),
                        "profile", Map.of(
                                "old", Map.of("token", "old-token"),
                                "new", Map.of("token", "new-token"))));

        ArgumentCaptor<PlatformAuditOutboxEvent> captor = ArgumentCaptor.forClass(PlatformAuditOutboxEvent.class);
        verify(repository).save(captor.capture());
        var payload = captor.getValue().getPayload();
        assertEquals(1000, payload.path("old_values").path("userLimit").asInt());
        assertEquals(1500, payload.path("new_values").path("userLimit").asInt());
        assertEquals("***", payload.path("old_values").path("password").asText());
        assertEquals("***", payload.path("new_values").path("password").asText());
        assertEquals("***", payload.path("old_values").path("profile").path("token").asText());
        assertEquals("***", payload.path("new_values").path("profile").path("token").asText());
        assertFalse(payload.toString().contains("old-secret"));
        assertFalse(payload.toString().contains("new-secret"));
    }
    @Test
    void recordsDeniedResourceAttemptWithDeniedOutcome() {
        PlatformAuditOutboxEventRepository repository = mock(PlatformAuditOutboxEventRepository.class);
        PlatformAuditWriter writer = new PlatformAuditWriter(repository);
        UUID actorId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        PlatformPrincipal actor = new PlatformPrincipal(actorId, null, "owner@example.test", false);

        writer.recordDenied(actor, "PLAN_MANAGE", "PLAN", planId);

        ArgumentCaptor<PlatformAuditOutboxEvent> captor = ArgumentCaptor.forClass(PlatformAuditOutboxEvent.class);
        verify(repository).save(captor.capture());
        assertEquals("DENIED", captor.getValue().getOutcome());
        assertEquals("DENIED", captor.getValue().getOperation());
        assertEquals("PLAN_MANAGE", captor.getValue().getActionCode());
        assertEquals("PLAN", captor.getValue().getEntityType());
        assertEquals(planId, captor.getValue().getEntityId());
    }

}
