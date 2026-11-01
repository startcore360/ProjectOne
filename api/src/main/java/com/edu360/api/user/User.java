package com.edu360.api.user;

import java.time.Instant;

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
 * the platform admin, a teacher, or a student. Stores the hashed password
 * and the lockout info used by AuthService, never the real password.
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

    @Column(nullable = false)
    private String password;

    // Stored as the word "TEACHER"/"STUDENT"/etc. in the database, not as a plain
    // number, so the data stays readable and safe even if the Role list is reordered later.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Account lockout (5 failed attempts / 15 minutes) — see AuthService.login.
    @Column(nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column
    private Instant lockedUntil;
}
