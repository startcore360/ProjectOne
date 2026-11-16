package com.edu360.api.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.edu360.api.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

class PlatformIdempotencyServiceTest {
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    @Test
    void hashesEquivalentMapsDeterministically() {
        assertEquals(
                PlatformIdempotencyService.requestHash(Map.of("a", 1, "b", 2), mapper),
                PlatformIdempotencyService.requestHash(Map.of("b", 2, "a", 1), mapper));
    }

    @Test
    void differentBodiesProduceDifferentHashes() {
        assertNotEquals(
                PlatformIdempotencyService.requestHash(Map.of("amount", 10), mapper),
                PlatformIdempotencyService.requestHash(Map.of("amount", 11), mapper));
    }

    @Test
    void rejectsInvalidKeyBeforeDatabaseAccess() {
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:unused");
        PlatformIdempotencyService service = new PlatformIdempotencyService(dataSource, mapper);
        ApiException error = assertThrows(ApiException.class,
                () -> service.claim(UUID.randomUUID(), "TENANT_CREATE", "short", Map.of()));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", error.getErrorCode());
    }
}
