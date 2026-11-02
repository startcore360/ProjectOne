package com.edu360.api.exception;

/** Thrown when Keycloak rejects a login or refresh attempt (wrong password, revoked token). */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
