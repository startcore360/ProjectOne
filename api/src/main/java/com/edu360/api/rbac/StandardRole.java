package com.edu360.api.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WHAT: one row of ref.standard_role - a read-only platform-seeded template (all 71 standard
 * roles of Permission Matrix V3.3, 6 platform + 65 tenant-side). Seeded exactly once by
 * sql/05_ref_data.sql; the application must never INSERT/UPDATE/DELETE this table (Addendum
 * rule 3: standard roles are suggested templates, owned by the platform release, not by tenant
 * code) - see StandardRoleRepository for how that is enforced at the Java layer as well.
 */
@Entity
@Table(schema = "ref", name = "standard_role")
@Getter
@NoArgsConstructor
public class StandardRole {

    @Id
    @Column(name = "role_code")
    private String roleCode;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_level", nullable = false)
    private RoleLevel roleLevel;

    @Column(name = "introduced_in_version", nullable = false)
    private String introducedInVersion;

    @Column(name = "requires_two_step_login", nullable = false)
    private boolean requiresTwoStepLogin;

    @Column(name = "is_staff", nullable = false)
    private boolean staff;
}
