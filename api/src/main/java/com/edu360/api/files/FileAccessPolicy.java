package com.edu360.api.files;

import java.util.UUID;

import com.edu360.api.config.TenantPrincipal;

/**
 * Record ownership is deliberately a separate authorization boundary. A later domain module
 * registers a policy once its file-to-record relationship exists; the default denies access.
 */
public interface FileAccessPolicy {
    boolean canAccess(FileObject file, TenantPrincipal principal);
    boolean canAttach(FileObject file, TenantPrincipal principal, String recordType, UUID recordId);
}
