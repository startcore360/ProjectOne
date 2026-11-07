package com.edu360.api.selfservice;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leave_balance", schema = "app")
@Getter
@Setter
@NoArgsConstructor
public class UserLeaveBalance {

    @EmbeddedId
    private UserLeaveBalanceId id;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "staff_profile_id", nullable = false)
    private UUID staffProfileId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "leave_year", nullable = false)
    private short leaveYear;

    @Column(name = "credited_days", nullable = false)
    private BigDecimal creditedDays;

    @Column(name = "used_days", nullable = false)
    private BigDecimal usedDays;

    @Column(name = "credited_by", nullable = false)
    private UUID creditedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserLeaveBalance(UUID tenantId, UUID leaveBalanceId, UUID staffProfileId,
                            UUID leaveTypeId, short leaveYear, BigDecimal creditedDays, BigDecimal usedDays) {
        this.id = new UserLeaveBalanceId(tenantId, leaveBalanceId);
        this.staffProfileId = staffProfileId;
        this.leaveTypeId = leaveTypeId;
        this.leaveYear = leaveYear;
        this.creditedDays = creditedDays;
        this.usedDays = usedDays != null ? usedDays : BigDecimal.ZERO;
        this.creditedBy = staffProfileId;
        this.createdAt = Instant.now();
    }
}
