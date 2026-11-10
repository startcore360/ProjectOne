package com.edu360.api.imports.dto;

import java.util.List;

public record ImportTemplateResponse(
        String importKind,
        String filename,
        List<String> headers,
        List<List<String>> sampleRows,
        String csvTemplate
) {}
