package com.edu360.api.platformops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.edu360.api.exception.ApiException;

class PlatformConcurrencyTest {
    private final PlatformConcurrency concurrency = new PlatformConcurrency();

    @Test
    void formatsStrongEtagFromRowVersion() {
        assertEquals("\"7\"", concurrency.etag(7));
    }

    @Test
    void requiresIfMatch() {
        ApiException error = assertThrows(ApiException.class, () -> concurrency.requireIfMatch(null, 7));
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, error.getStatus());
        assertEquals("IF_MATCH_REQUIRED", error.getErrorCode());
    }

    @Test
    void rejectsStaleVersion() {
        ApiException error = assertThrows(ApiException.class, () -> concurrency.requireIfMatch("\"6\"", 7));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("VERSION_CONFLICT", error.getErrorCode());
    }

    @Test
    void acceptsCurrentVersion() {
        concurrency.requireIfMatch("\"7\"", 7);
    }
}
