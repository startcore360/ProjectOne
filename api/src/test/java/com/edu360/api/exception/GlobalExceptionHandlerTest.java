package com.edu360.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

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

    @Test
    void unhandledExceptionHidesInternalDetailsAndRetainsCorrelationId() {
        String sensitiveInternalMessage = "PSQLException: relation 'app.user_account' violates foreign key constraint";
        var response = handler.handleGeneral(new RuntimeException(sensitiveInternalMessage), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(response.getBody()).containsKey("correlationId");
        assertThat(response.getBody().get("correlationId")).asString().isNotBlank();
        assertThat(response.getBody()).doesNotContainKey("detail");
        assertThat(response.getBody().get("error")).asString().doesNotContain(sensitiveInternalMessage);
        assertThat(response.getBody().get("message")).asString().doesNotContain(sensitiveInternalMessage);
    }

    @Test
    void unexpectedErrorsNeverLeakExceptionDetailsToTheClient() {
        var response = handler.handleGeneral(
                new IllegalStateException("SELECT * FROM app.user_account WHERE secret = 'x'"), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("code", "INTERNAL_ERROR").containsKey("correlationId");
        assertThat(response.getBody()).doesNotContainKey("detail");
        String everything = String.valueOf(response.getBody());
        assertThat(everything).doesNotContain("IllegalStateException").doesNotContain("SELECT").doesNotContain("secret");
    }

    @Test
    void duplicateWithoutFieldNameEmitsConflictWithoutFieldErrors() {
        var response = handler.handleDuplicate(
                new DuplicateResourceException("Entity already exists"), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "DUPLICATE_RESOURCE");
        assertThat(response.getBody()).doesNotContainKey("fieldErrors");
    }

    @Test
    void duplicateWithFieldNameEmitsConflictWithFieldErrorsMap() {
        var response = handler.handleDuplicate(
                new DuplicateResourceException("Email already taken", "email"), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "DUPLICATE_RESOURCE");
        assertThat(response.getBody()).containsKey("fieldErrors");
        @SuppressWarnings("unchecked")
        var fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("email", "Email already taken");
    }
}
