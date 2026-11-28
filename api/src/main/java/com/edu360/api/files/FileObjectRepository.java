package com.edu360.api.files;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface FileObjectRepository extends Repository<FileObject, FileObjectId> {
    FileObject save(FileObject fileObject);
    Optional<FileObject> findById(FileObjectId id);
    List<FileObject> findByIdTenantIdAndUploadedByAndPurpose(UUID tenantId, UUID uploadedBy, String purpose);
    Optional<FileObject> findFirstByIdTenantIdAndUploadedByAndPurposeAndStatusAndScanStatus(
            UUID tenantId, UUID uploadedBy, String purpose, String status, String scanStatus);
}
