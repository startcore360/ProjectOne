package com.edu360.api.academic;

import jakarta.validation.constraints.NotBlank;

public record PromotionDecisionRequest(
        @NotBlank String decision, // APPROVED or REJECTED
        String comment
) {}
