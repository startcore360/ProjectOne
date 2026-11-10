package com.edu360.api.platformops;

import static org.mockito.Mockito.mock;

import com.edu360.api.platformops.role.PlatformAuditWriter;

/** Builds the real permission service for tests; denied-attempt audit goes to a mock writer unless a test passes its own. */
public final class PlatformTestSupport {

    private PlatformTestSupport() {}

    public static PlatformPermissionService permissions(PlatformAccessLookup lookup) {
        return com.edu360.api.platformops.PlatformTestSupport.permissions(lookup, mock(PlatformAuditWriter.class));
    }

    public static PlatformPermissionService permissions(PlatformAccessLookup lookup, PlatformAuditWriter writer) {
        return new PlatformPermissionService(lookup, writer);
    }
}
