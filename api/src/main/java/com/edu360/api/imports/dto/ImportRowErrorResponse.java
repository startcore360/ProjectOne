package com.edu360.api.imports.dto;

import java.util.UUID;

import com.edu360.api.imports.ImportRowError;

public record ImportRowErrorResponse(
        UUID id,
        int rowNumber,
        String fieldName,
        String errorCode,
        String errorMessage,
        String rowData
) {
    public static ImportRowErrorResponse from(ImportRowError error) {
        return new ImportRowErrorResponse(
                error.getId().getImportRowErrorId(),
                error.getRowNumber(),
                error.getFieldName(),
                error.getErrorCode(),
                error.getErrorMessage(),
                error.getRowData()
        );
    }
}
