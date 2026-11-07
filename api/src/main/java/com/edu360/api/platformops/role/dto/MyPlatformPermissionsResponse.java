package com.edu360.api.platformops.role.dto;

import java.util.List;
import java.util.Set;

import com.edu360.api.platform.PlatformRole;

/** What the signed-in person can do now, for menus only. The server still checks every call. */
public record MyPlatformPermissionsResponse(
        PlatformRole platformRole,
        String customRoleName,
        Set<String> actions,
        List<String> stepInRequired,
        List<String> steppedIn
) {
}
