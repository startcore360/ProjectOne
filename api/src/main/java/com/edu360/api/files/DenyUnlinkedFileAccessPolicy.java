package com.edu360.api.files;

import java.util.UUID;

import com.edu360.api.config.TenantPrincipal;

/**
 * Test/fallback policy only. The production Spring bean is JdbcFileAccessPolicy, which resolves
 * the tenant-qualified owning record before action authorization.
 */
@Deprecated(forRemoval = false)
public class DenyUnlinkedFileAccessPolicy implements FileAccessPolicy {
    @Override
    public boolean canAccess(FileObject file, TenantPrincipal principal) { return false; }

    @Override
    public boolean canAttach(FileObject file, TenantPrincipal principal, String recordType, UUID recordId) { return false; }
}
