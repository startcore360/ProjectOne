package com.edu360.api.attendance.staffcheckin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionAttendanceLocationRepository
        extends JpaRepository<InstitutionAttendanceLocation, InstitutionAttendanceLocation.Pk> {

    Optional<InstitutionAttendanceLocation> findByTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);

    List<InstitutionAttendanceLocation> findByTenantId(UUID tenantId);
}
