package com.edu360.api.institution.dto;

import java.util.UUID;

/** Response for a successful STUDENT_LOGIN_SWITCH. */
public record StudentLoginSwitchResponse(UUID classId, boolean studentLoginEnabled) {
}
