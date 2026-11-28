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

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(name = "admission_date")
    private java.time.LocalDate admissionDate;

    @Column(name = "national_id_last4")
    private String nationalIdLast4;

    @Column(name = "photo_file_id")
    private UUID photoFileId;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;

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
