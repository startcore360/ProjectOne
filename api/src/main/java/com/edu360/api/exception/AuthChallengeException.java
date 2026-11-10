package com.edu360.api.exception;

import java.util.Map;

/**
 * A sign-in that cannot finish yet, with a machine-readable code the screen acts on (B3):
 * MFA_REQUIRED (401, ask for the authenticator code), MFA_ENROLMENT_REQUIRED (401, hand off to
 * Keycloak's authenticator set-up page - details carry enrolmentUrl), LOCKED (423, Keycloak
 * brute-force lockout), INVALID_CREDENTIALS (401). GlobalExceptionHandler renders it in the
 * standard {code, message, status, ...} shape plus the details.
 */
public class AuthChallengeException extends RuntimeException {

    private final int status;
    private final String code;
    private final transient Map<String, Object> details;

    public AuthChallengeException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public AuthChallengeException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public static AuthChallengeException mfaRequired() {
        return new AuthChallengeException(401, "MFA_REQUIRED",
                "Enter the 6-digit code from your authenticator app to finish signing in.");
    }

    public static AuthChallengeException invalidCredentials() {
        return new AuthChallengeException(401, "INVALID_CREDENTIALS", "Invalid credentials");
    }

    public static AuthChallengeException invalidCode() {
        return new AuthChallengeException(401, "INVALID_CREDENTIALS", "Invalid credentials or authenticator code");
    }

    public static AuthChallengeException locked(int lockoutMinutes) {
        return new AuthChallengeException(423, "LOCKED",
                "This account is temporarily locked after too many failed attempts. Try again after "
                        + lockoutMinutes + " minutes or ask your administrator to unlock it.");
    }
}
