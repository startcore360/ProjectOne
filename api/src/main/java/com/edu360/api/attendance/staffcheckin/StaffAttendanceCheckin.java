package com.edu360.api.attendance.staffcheckin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps app.staff_attendance_checkin (sql/76_v41_staff_attendance_checkin.sql). Capture columns
 * (institution, staff, photo, captured_at, attendance_date, geo, accuracy, distance, device_type,
 * liveness, created_by) are written once on insert and mapped updatable=false, so Hibernate never sends
 * them in an UPDATE; app.fn_checkin_capture_guard is the hard guard in the database. Only status,
 * approval_request_id and the review columns change afterwards. row_version is maintained by the
 * trg_touch trigger on every update, so it is insert-only here as well.
 */
@Entity
@Table(schema = "app", name = "staff_attendance_checkin")
@IdClass(StaffAttendanceCheckin.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffAttendanceCheckin {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Id
    @Column(name = "staff_attendance_checkin_id")
    private UUID staffAttendanceCheckinId;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private UUID institutionId;

    @Column(name = "staff_user_id", nullable = false, updatable = false)
    private UUID staffUserId;

    @Column(name = "photo_file_id", nullable = false, updatable = false)
    private UUID photoFileId;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    /** Institution-local date of capturedAt; unique per staff member (one check-in per day). */
    @Column(name = "attendance_date", nullable = false, updatable = false)
    private LocalDate attendanceDate;

    @Column(name = "latitude", nullable = false, updatable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, updatable = false)
    private BigDecimal longitude;

    @Column(name = "gps_accuracy_meters", nullable = false, updatable = false)
    private BigDecimal gpsAccuracyMeters;

    @Column(name = "distance_from_institution_m", nullable = false, updatable = false)
    private BigDecimal distanceFromInstitutionM;

    @Column(name = "device_type", updatable = false)
    private String deviceType;

    @Column(name = "liveness_confidence", nullable = false, updatable = false)
    private BigDecimal livenessConfidence;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false, updatable = false)
    private Integer rowVersion;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements java.io.Serializable {
        private UUID tenantId;
        private UUID staffAttendanceCheckinId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk other)) return false;
            return java.util.Objects.equals(tenantId, other.tenantId)
                    && java.util.Objects.equals(staffAttendanceCheckinId, other.staffAttendanceCheckinId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tenantId, staffAttendanceCheckinId);
        }
    }
}
