package com.edu360.api.attendance.staffcheckin;

import java.time.Instant;
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
 * Maps app.institution_attendance_location (sql/76_v41_staff_attendance_checkin.sql). One row
 * per institution: geofence centre/radius, GPS accuracy threshold, retention and the
 * configurable approver role for the Principal's own escalations (approver_for_principal_role).
 */
@Entity
@Table(schema = "app", name = "institution_attendance_location")
@IdClass(InstitutionAttendanceLocation.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class InstitutionAttendanceLocation {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Id
    @Column(name = "institution_attendance_location_id")
    private UUID institutionAttendanceLocationId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "latitude", nullable = false)
    private java.math.BigDecimal latitude;

    @Column(name = "longitude", nullable = false)
    private java.math.BigDecimal longitude;

    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;

    @Column(name = "gps_accuracy_threshold_meters", nullable = false)
    private Integer gpsAccuracyThresholdMeters;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays;

    /** Role code the Principal's own check-ins escalate to (e.g. VICE_CHANCELLOR, MANAGEMENT). */
    @Column(name = "approver_for_principal_role")
    private String approverForPrincipalRole;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements java.io.Serializable {
        private UUID tenantId;
        private UUID institutionAttendanceLocationId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk other)) return false;
            return java.util.Objects.equals(tenantId, other.tenantId)
                    && java.util.Objects.equals(institutionAttendanceLocationId, other.institutionAttendanceLocationId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tenantId, institutionAttendanceLocationId);
        }
    }
}
