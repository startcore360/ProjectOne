package com.edu360.api.attendance.staffcheckin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffReferencePhotoRepository
        extends JpaRepository<StaffReferencePhoto, StaffReferencePhoto.Pk> {

    Optional<StaffReferencePhoto> findFirstByTenantIdAndStaffUserIdOrderByUploadedAtDesc(UUID tenantId, UUID staffUserId);
}
