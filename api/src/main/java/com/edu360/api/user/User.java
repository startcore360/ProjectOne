package com.edu360.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row in the "users" database table: a single account, whether that's
 * the platform admin, a teacher, or a student. Keycloak owns the password and
 * login; this row only holds institutional data plus the Keycloak user id
 * (OD-10) that links the two.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    // Keycloak's own "sub" claim for this user. The authoritative link between this row
    // and the Keycloak account (OD-10) — never matched by email, since email can change.
    @Column(nullable = false, unique = true, updatable = false)
    private String keycloakId;

    // Stored as the word "TEACHER"/"STUDENT"/etc. in the database, not as a plain
    // number, so the data stays readable and safe even if the Role list is reordered later.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Keycloak's own "temporary password, must change" required action cannot be used here -
    // it blocks the Direct Grant flow outright instead of letting login succeed with a flag to
    // act on (see docs/KEYCLOAK_MIGRATION_PLAN.md). This column is this app's own replacement:
    // true for every account register() creates, so the admin-chosen initial password must be
    // replaced before anything else is usable; MustChangePasswordFilter enforces that server-side.
    @Column(nullable = false)
    @Builder.Default
    private boolean mustChangePassword = true;
}
