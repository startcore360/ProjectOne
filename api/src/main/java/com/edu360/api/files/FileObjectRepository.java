package com.edu360.api.files;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * No delete method, deliberately: a file_object row is replaced by flipping status to DELETED
 * (see FileObject's class comment), never removed - same "ended, never deleted" convention as
 * UserRoleAssignmentRepository. Extends the bare marker {@link Repository} so a DELETE capability
 * cannot reappear through an inherited default method.
 */
public interface FileObjectRepository extends Repository<FileObject, FileObjectId> {

    FileObject save(FileObject fileObject);

    Optional<FileObject> findById(FileObjectId id);

    /** Every file this user has ever uploaded for this purpose (ACTIVE and DELETED alike) -
     *  used to end the previous photo(s) when a new one replaces it. */
    List<FileObject> findByIdTenantIdAndUploadedByAndPurpose(UUID tenantId, UUID uploadedBy, String purpose);

    /** The one file that should actually be served/shown right now: this user's current,
     *  scanned-clean, active file for this purpose. Used by both the self-service photo
     *  endpoints and AuthService's photoUrl resolution (see AuthService.resolvePhotoUrl) - both
     *  must agree on exactly this definition of "has a usable photo". */
    Optional<FileObject> findFirstByIdTenantIdAndUploadedByAndPurposeAndStatusAndScanStatus(
            UUID tenantId, UUID uploadedBy, String purpose, String status, String scanStatus);
}
