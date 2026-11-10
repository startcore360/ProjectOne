package com.edu360.api.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.edu360.api.config.keycloak.KeycloakProvisioningException;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The one place that turns a thrown error into an HTTP response. Instead of
 * every controller writing its own try/catch, a controller can just throw
 * (for example) a ResourceNotFoundException, and this class catches it and
 * turns it into a proper 404 with a consistent D5 {code, message, timestamp, path, status, error} body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found: " + ex.getResourcePath(), request);
    }

    /** Part 9 section 2.7: an unknown address is 404, never 500. */
    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(org.springframework.web.servlet.NoHandlerFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found: " + ex.getRequestURL(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), request);
    }

    /** P-01: deciding your own request is 403, not 422. */
    @ExceptionHandler(SelfApprovalForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleSelfApproval(SelfApprovalForbiddenException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRuleViolation(BusinessRuleViolationException ex, HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("error", ex.getMessage());
        body.put("errorCode", ex.getErrorCode());
        body.put("code", ex.getErrorCode());
        body.put("message", ex.getMessage());
        if (request != null) {
            body.put("path", request.getRequestURI());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request);
    }

    /** A-02: several active roles and none chosen - 409, the screen sends the user to the role switcher. */
    @ExceptionHandler(ActiveRoleRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleActiveRoleRequired(ActiveRoleRequiredException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ActiveRoleRequiredException.CODE, ex.getMessage(), request);
    }

    @ExceptionHandler(KeycloakProvisioningException.class)
    public ResponseEntity<Map<String, Object>> handleKeycloakProvisioning(KeycloakProvisioningException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Account provisioning is temporarily unavailable", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    /** B3: sign-in challenges (MFA_REQUIRED, MFA_ENROLMENT_REQUIRED, LOCKED, INVALID_CREDENTIALS). */
    @ExceptionHandler(AuthChallengeException.class)
    public ResponseEntity<Map<String, Object>> handleAuthChallenge(AuthChallengeException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatus());
        ResponseEntity<Map<String, Object>> base = buildResponse(status, ex.getCode(), ex.getMessage(), request);
        Map<String, Object> body = new HashMap<>(base.getBody());
        body.putAll(ex.getDetails());
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }

    /** B3 / I-07: the tenant's plan or override has this module switched off. */
    @ExceptionHandler(ModuleNotEnabledException.class)
    public ResponseEntity<Map<String, Object>> handleModuleNotEnabled(ModuleNotEnabledException ex, HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> base = buildResponse(HttpStatus.FORBIDDEN, "MODULE_NOT_ENABLED", ex.getMessage(), request);
        Map<String, Object> body = new HashMap<>(base.getBody());
        body.put("moduleCode", ex.getModuleCode());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed JSON request body", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", "Invalid parameter: " + ex.getName(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("code", "VALIDATION_FAILED");
        body.put("error", "Validation failed");
        body.put("message", "Validation failed");
        body.put("fieldErrors", fieldErrors);
        if (request != null) {
            body.put("path", request.getRequestURI());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Database integrity violation: {}", ex.getMessage());
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("unique") || msg.contains("duplicate")) {
            return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "A record with this identifier already exists.", request);
        } else if (msg.contains("foreign key") || msg.contains("referential integrity") || msg.contains("violates foreign key constraint")) {
            return buildResponse(HttpStatus.NOT_FOUND, "REFERENCE_NOT_FOUND", "Referenced entity was not found in the current scope.", request);
        } else {
            return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", "The requested operation violates a business rule constraint.", request);
        }
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Input validation constraint violated.", request);
    }

    @ExceptionHandler(FeatureNotAvailableException.class)
    public ResponseEntity<Map<String, Object>> handleNotAvailable(FeatureNotAvailableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_IMPLEMENTED, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex, HttpServletRequest request) {
        String correlationId = java.util.UUID.randomUUID().toString();
        log.error("Unhandled exception reached GlobalExceptionHandler [correlationId={}]", correlationId, ex);
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("code", "INTERNAL_ERROR");
        body.put("error", "An unexpected error occurred. Please contact support quoting correlation ID: " + correlationId);
        body.put("message", "An unexpected error occurred. Please contact support quoting correlation ID: " + correlationId);
        body.put("correlationId", correlationId);
        if (request != null) {
            body.put("path", request.getRequestURI());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String code, String message, HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("code", code);
        body.put("error", message);
        body.put("message", message);
        if (request != null) {
            body.put("path", request.getRequestURI());
        }
        return ResponseEntity.status(status).body(body);
    }
}
