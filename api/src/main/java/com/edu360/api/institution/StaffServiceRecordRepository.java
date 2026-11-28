package com.edu360.api.institution;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffServiceRecordRepository extends JpaRepository<StaffServiceRecord, StaffServiceRecordId> {
    List<StaffServiceRecord> findByIdTenantIdAndStaffProfileIdOrderByEffectiveOnDesc(
            UUID tenantId, UUID staffProfileId);
}
