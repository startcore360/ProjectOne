package com.edu360.api.institution.dto;

import java.util.List;
import java.util.UUID;

public record InstitutionHierarchyResponse(
        UUID tenantId,
        String tenantName,
        String tenantType,
        List<InstitutionHierarchyNode> institutions
) {}
