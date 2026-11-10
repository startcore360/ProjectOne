package com.edu360.api.institution.dto;

import java.util.List;

public record InstitutionPageResponse(
        List<InstitutionResponse> items,
        long total
) {}
