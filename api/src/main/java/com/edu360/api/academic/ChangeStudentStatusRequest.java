package com.edu360.api.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeStudentStatusRequest(
        @NotBlank String status, // ACTIVE, ON_HOLD, WITHDRAWN, TRANSFERRED, DROPPED, DETAINED, GRADUATED
        @NotBlank @Size(min = 1, max = 500) String reason
) {}
