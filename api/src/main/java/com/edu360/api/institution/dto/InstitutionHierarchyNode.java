package com.edu360.api.institution.dto;

import java.util.List;

public record InstitutionHierarchyNode(
        InstitutionResponse institution,
        List<DepartmentResponse> departments
) {}
