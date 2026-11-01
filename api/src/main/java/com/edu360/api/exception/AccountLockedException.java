package com.edu360.api.exception;

/**
 * Thrown when someone tries to log in to an account that's currently
 * locked (too many wrong passwords in a row). GlobalExceptionHandler
 * turns this into a 423 "Locked" response for the caller.
 */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
