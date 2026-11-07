package com.edu360.api.selfservice;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leave_request", schema = "app")
@Getter
@Setter
@NoArgsConstructor
public class UserLeaveRequest {

    @EmbeddedId
    private UserLeaveRequestId id;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "for_staff_profile_id")
    private UUID forStaffProfileId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "days_counted", nullable = false)
    private BigDecimal daysCounted;

    @Column(name = "process_code", nullable = false)
    private String processCode;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserLeaveRequest(UUID tenantId, UUID leaveRequestId, UUID requestedByUserId,
                            UUID leaveTypeId, String unit, LocalDate startDate, LocalDate endDate,
                            String reason, BigDecimal daysCounted, String processCode, String status) {
        this.id = new UserLeaveRequestId(tenantId, leaveRequestId);
        this.requestedByUserId = requestedByUserId;
        this.leaveTypeId = leaveTypeId;
        this.unit = unit;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.daysCounted = daysCounted;
        this.processCode = processCode;
        this.status = status != null ? status : "DRAFT";
        this.createdAt = Instant.now();
    }
}
