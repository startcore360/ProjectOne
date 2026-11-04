package com.edu360.api.institution;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: a mapping of app.school_class - a class of a school (schools only). studentLoginEnabled
 * is exactly the STUDENT_LOGIN_SWITCH column (schema comment: "View-only login for chosen
 * classes (STUDENT_LOGIN_SWITCH) [PR-04]").
 */
@Entity
@Table(schema = "app", name = "school_class")
@Getter
@Setter
@NoArgsConstructor
public class SchoolClass {

    @Id
    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "student_login_enabled", nullable = false)
    private boolean studentLoginEnabled;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}
