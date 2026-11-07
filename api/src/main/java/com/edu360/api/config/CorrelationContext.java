package com.edu360.api.config;

import java.util.UUID;

/**
 * WHAT: Holds the request correlation ID (OD-09) for the lifetime of one HTTP request, on one
 * worker thread. Provides traceability across logs, database audit outboxes, and client responses.
 */
public final class CorrelationContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static void set(String correlationId) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            CURRENT.set(UUID.randomUUID().toString());
        } else {
            CURRENT.set(correlationId.trim());
        }
    }

    public static String get() {
        String id = CURRENT.get();
        if (id == null) {
            id = UUID.randomUUID().toString();
            CURRENT.set(id);
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
