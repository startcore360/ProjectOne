package com.edu360.api.exception;

/**
 * Thrown when someone is logged in but not allowed to do the thing they're
 * trying to do. Despite the name, GlobalExceptionHandler turns this into a
 * 403 Forbidden response, not a 401 — the caller IS known, they're just blocked.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
