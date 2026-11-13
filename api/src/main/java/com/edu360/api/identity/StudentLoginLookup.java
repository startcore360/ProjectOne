package com.edu360.api.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * I-02 / A8 (D1 §8.1, story 1): "College students use Student ID and password"; school students are enabled per class. Finds the ACTIVE
 * login account that owns a student code in the tenant already set in TenantContext, through
 * app.fn_student_login_account (sql/26_v41_student_login_switch_guard.sql) - a narrow SECURITY DEFINER
 * function, because the profile row is institution-scoped under RLS and nobody is signed in yet.
 * Answers nothing but the account id; never widens the session's institution scope.
 */
@Component
public class StudentLoginLookup {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Resolves a verified secondary email alias to its owning account. The caller must have
     * established the tenant from the login host before setting TenantContext.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> accountForVerifiedEmailAlias(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        List<?> rows = entityManager.createNativeQuery("""
                SELECT user_account_id
                  FROM app.user_login_alias
                 WHERE tenant_id = current_setting('app.tenant_id')::uuid
                   AND email = lower(btrim(:email))
                   AND is_verified = true
                 LIMIT 1
                """)
                .setParameter("email", email)
                .getResultList();
        if (rows.isEmpty() || rows.get(0) == null) return Optional.empty();
        Object value = rows.get(0);
        return Optional.of(value instanceof UUID uuid ? uuid : UUID.fromString(value.toString()));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> accountForStudentCode(String studentCode) {
        if (studentCode == null || studentCode.isBlank()) {
            return Optional.empty();
        }
        List<?> rows = entityManager.createNativeQuery("SELECT app.fn_student_login_account(:code)")
                .setParameter("code", studentCode.trim())
                .getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        Object value = rows.get(0);
        return Optional.of(value instanceof UUID uuid ? uuid : UUID.fromString(value.toString()));
    }
}
