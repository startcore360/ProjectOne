package com.edu360.api.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * I-02 (D1 §8.1, story 1): "College students use Student ID and password". Finds the ACTIVE
 * login account that owns a student code in the tenant already set in TenantContext, through
 * app.fn_student_login_account (sql/24_v41_sign_in_security.sql) - a narrow SECURITY DEFINER
 * function, because the profile row is institution-scoped under RLS and nobody is signed in yet.
 * Answers nothing but the account id; never widens the session's institution scope.
 */
@Component
public class StudentLoginLookup {

    @PersistenceContext
    private EntityManager entityManager;

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
