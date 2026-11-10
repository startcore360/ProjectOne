package com.edu360.api.audit;

/**
 * A required audit event could not be stored (L-07). Deliberately unchecked and unhandled: it reaches
 * GlobalExceptionHandler as a 500 with a correlation id, and the surrounding transaction rolls back.
 */
public class AuditFailureException extends RuntimeException {

    public AuditFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
