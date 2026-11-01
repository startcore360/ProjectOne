package com.edu360.api.exception;

/**
 * Thrown when code looks something up (a course, a student, etc.) and it
 * doesn't exist. GlobalExceptionHandler turns this into a 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
