package com.edu360.api.identity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "app", name = "student_profile")
@Getter
@Setter
@NoArgsConstructor
public class StudentProfile {

    @Id
    @Column(name = "student_profile_id")
    private UUID studentProfileId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "user_account_id")
    private UUID userAccountId;

    @Column(name = "student_code", nullable = false)
    private String studentCode;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    public StudentProfile(UUID studentProfileId, UUID tenantId, UUID institutionId,
                          UUID userAccountId, String studentCode, String status) {
        this.studentProfileId = studentProfileId;
        this.tenantId = tenantId;
        this.institutionId = institutionId;
        this.userAccountId = userAccountId;
        this.studentCode = studentCode;
        this.status = status != null ? status : "ACTIVE";
    }
}
