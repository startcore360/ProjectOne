package com.edu360.api.institution;

public record InstitutionIdFormatsResponse(
        String studentIdFormat,
        String staffIdFormat,
        boolean studentFormatLocked,
        boolean staffFormatLocked
) {}
