package com.edu360.api.academic;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateSubjectAllocationRequest(
        @NotNull UUID institutionId,
        @NotNull UUID courseOfferingId,
        UUID sectionId,
        UUID subjectGroupId,
        @NotNull UUID staffAssignmentId
) {}
