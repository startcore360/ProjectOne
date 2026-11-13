package com.edu360.api.institution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "app", name = "staff_profile")
@Getter
@Setter
@NoArgsConstructor
public class StaffProfile {
    @EmbeddedId
    private StaffProfileId id;
    @Column(name = "user_account_id")
    private UUID userAccountId;
    @Column(name = "staff_code", nullable = false)
    private String staffCode;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(name = "personal_email")
    private String personalEmail;
    @Column(name = "personal_mobile")
    private String personalMobile;
    @Column(name = "national_id_last4")
    private String nationalIdLast4;
    @Column(name = "photo_file_id")
    private UUID photoFileId;
    @Column(name = "employment_status", nullable = false)
    private String employmentStatus = "ACTIVE";
    @Column(name = "joined_on")
    private LocalDate joinedOn;
    @Column(name = "left_on")
    private LocalDate leftOn;
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

    public UUID getTenantId() { return id == null ? null : id.getTenantId(); }
    public UUID getStaffProfileId() { return id == null ? null : id.getStaffProfileId(); }
}
