package com.edu360.api.institution.dto;

import java.util.UUID;

public record StudentLoginSwitchResponse(UUID classId, boolean studentLoginEnabled, int rowVersion) {
    public StudentLoginSwitchResponse(UUID classId, boolean studentLoginEnabled) {
        this(classId, studentLoginEnabled, 1);
    }
}
