package com.edu360.api.files;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.edu360.api.config.TenantPrincipal;

/** Fail-closed until study-material, assignment, syllabus and staff record associations are registered. */
@Component
public class DenyUnlinkedFileAccessPolicy implements FileAccessPolicy {
    @Override
    public boolean canAccess(FileObject file, TenantPrincipal principal) {
        return false;
    }

    @Override
    public boolean canAttach(FileObject file, TenantPrincipal principal, String recordType, UUID recordId) {
        return false;
    }
}
