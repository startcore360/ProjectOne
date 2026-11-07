package com.edu360.api.exception;

import lombok.Getter;

/**
 * Thrown when an operation violates a statutory, domain or workflow rule
 * (e.g., GO_LIVE_BLOCKED under rule C7, or PLAN_LIMIT_REACHED under WF-14).
 * Mapped to HTTP 422 Unprocessable Entity with a structured error payload.
 */
@Getter
public class BusinessRuleViolationException extends RuntimeException {

    private final String errorCode;

    public BusinessRuleViolationException(String message) {
        super(message);
        this.errorCode = "BUSINESS_RULE_VIOLATION";
    }

    public BusinessRuleViolationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
