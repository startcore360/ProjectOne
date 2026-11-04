package com.edu360.api.institution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterRepository extends JpaRepository<Semester, UUID> {

    Optional<Semester> findByTenantIdAndSemesterId(UUID tenantId, UUID semesterId);
}
