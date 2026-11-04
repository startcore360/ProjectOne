package com.edu360.api.identity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WHAT: a read-only view of app.student_profile - only the two columns needed to re-verify a
 * STUDENT_CODE login during password change (see AuthService.identifierFor). This is not the
 * full student record (name, admission date, status, etc.) - that is the Users-and-roles epic's
 * job, not this auth-path lookup.
 */
@Entity
@Table(schema = "app", name = "student_profile")
@Getter
@NoArgsConstructor
public class StudentProfile {

    @Id
    @Column(name = "student_profile_id")
    private UUID studentProfileId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_account_id")
    private UUID userAccountId;

    @Column(name = "student_code", nullable = false)
    private String studentCode;
}
