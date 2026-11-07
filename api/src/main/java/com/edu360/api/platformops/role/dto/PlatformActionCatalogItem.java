package com.edu360.api.platformops.role.dto;

/** One row of the permission picker. lockedReason is set when the action can't be given at all. */
public record PlatformActionCatalogItem(
        String actionCode,
        String description,
        String moduleName,
        boolean sensitive,
        String lockedReason
) {
}
