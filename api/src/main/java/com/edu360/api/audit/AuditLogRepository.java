package com.edu360.api.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Lets the rest of the app save and read AuditLog rows from the database,
 * without writing any SQL — Spring Data JPA fills in the basic save/find
 * methods automatically just from this interface.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
