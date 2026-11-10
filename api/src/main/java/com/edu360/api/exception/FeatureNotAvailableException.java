package com.edu360.api.exception;

/**
 * Something the product shows or names but has not built yet (rule R7: say so honestly instead of faking it).
 * Mapped to HTTP 501 with a stable code by GlobalExceptionHandler.
 */
public class FeatureNotAvailableException extends RuntimeException {

    private final String code;

    public FeatureNotAvailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
