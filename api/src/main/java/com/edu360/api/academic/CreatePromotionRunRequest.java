package com.edu360.api.academic;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePromotionRunRequest(
        @NotNull UUID institutionId,
        @NotNull UUID fromSessionId,
        @NotNull UUID toSessionId
) {}
