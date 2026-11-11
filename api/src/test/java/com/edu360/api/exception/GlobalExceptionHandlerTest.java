package com.edu360.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.edu360.api.audit.AuditFailureException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsConflictBusinessRulesTo409() {
        for (String code : new String[] {"TERM_LOCKED", "ATTENDANCE_ALREADY_SUBMITTED",
                "ASSIGNMENT_CLOSED", "VERSION_CONFLICT", "FILE_NOT_AVAILABLE"}) {
            var response = handler.handleBusinessRuleViolation(
                    new BusinessRuleViolationException(code, "conflict"), null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).containsEntry("code", code);
        }
    }

    @Test
    void mapsPayloadLimitTo413() {
        var response = handler.handleBusinessRuleViolation(
                new BusinessRuleViolationException("PAYLOAD_TOO_LARGE", "too large"), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void mapsRequestPreconditionsTo400AndDomainRulesTo422() {
        for (String code : new String[] {"IF_MATCH_REQUIRED", "IDEMPOTENCY_KEY_REQUIRED",
                "INVALID_CURSOR", "INVALID_QUERY"}) {
            assertThat(handler.handleBusinessRuleViolation(
                    new BusinessRuleViolationException(code, "bad request"), null).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        for (String code : new String[] {"NOT_ASSIGNED", "NOT_ENROLLED", "ENTRIES_INCOMPLETE",
                "ATTENDANCE_WINDOW_CLOSED", "FILE_TYPE_NOT_ALLOWED", "MEETING_PROVIDER_NOT_CONFIGURED"}) {
            assertThat(handler.handleBusinessRuleViolation(
                    new BusinessRuleViolationException(code, "domain rule"), null).getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Test
    void auditWriteFailureReturns503AuditUnavailable() {
        var response = handler.handleAuditFailure(
                new AuditFailureException("audit store unavailable", new IllegalStateException("write failed")), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("code", "AUDIT_UNAVAILABLE");
        assertThat(response.getBody()).containsKey("error");
    }
}
