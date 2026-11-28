package com.edu360.api.attendance.staffcheckin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffAttendanceCheckinRepository
        extends JpaRepository<StaffAttendanceCheckin, StaffAttendanceCheckin.Pk> {

    Optional<StaffAttendanceCheckin> findByTenantIdAndStaffAttendanceCheckinId(UUID tenantId, UUID staffAttendanceCheckinId);

    /** Row lock so a reviewer's decision and the day-end expiry job never interleave on one check-in. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM StaffAttendanceCheckin c WHERE c.tenantId = :tenantId AND c.staffAttendanceCheckinId = :checkinId")
    Optional<StaffAttendanceCheckin> findForUpdate(@Param("tenantId") UUID tenantId, @Param("checkinId") UUID checkinId);

    /** Own history, newest first, bounded so one call can never pull a staff member's whole record set. */
    List<StaffAttendanceCheckin> findTop100ByTenantIdAndStaffUserIdOrderByCapturedAtDesc(UUID tenantId, UUID staffUserId);

    List<StaffAttendanceCheckin> findByTenantIdAndStatusInAndApprovalRequestIdIsNotNull(UUID tenantId, Collection<String> statuses);

    List<StaffAttendanceCheckin> findByTenantIdAndInstitutionIdAndStatusAndCapturedAtBefore(
            UUID tenantId, UUID institutionId, String status, Instant before);

    /** One check-in per staff member per institution-local day (ux_staff_attendance_checkin_staff_day). */
    boolean existsByTenantIdAndStaffUserIdAndAttendanceDate(UUID tenantId, UUID staffUserId, LocalDate attendanceDate);

    /** A selfie file may back at most one check-in (no replaying an earlier capture). */
    boolean existsByTenantIdAndPhotoFileId(UUID tenantId, UUID photoFileId);
}
