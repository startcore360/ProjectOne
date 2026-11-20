package com.edu360.api.audit;

/**
 * A required audit event could not be stored (L-07). Deliberately unchecked; GlobalExceptionHandler
 * maps it to 503 AUDIT_UNAVAILABLE. The surrounding transaction rolls back.
 */
public class AuditFailureException extends RuntimeException {

    public AuditFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
