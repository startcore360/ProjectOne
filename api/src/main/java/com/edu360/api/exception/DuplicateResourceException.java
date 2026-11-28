package com.edu360.api.exception;

public class DuplicateResourceException extends RuntimeException {
    private final String fieldName;

    public DuplicateResourceException(String message) {
        this(message, null);
    }

    public DuplicateResourceException(String message, String fieldName) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}

