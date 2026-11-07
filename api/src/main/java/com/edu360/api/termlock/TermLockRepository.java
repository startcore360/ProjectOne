package com.edu360.api.termlock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TermLockRepository extends JpaRepository<TermLock, TermLockId> {

    List<TermLock> findByIdTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);

    Optional<TermLock> findByIdTenantIdAndIdTermLockId(UUID tenantId, UUID termLockId);

    Optional<TermLock> findByIdTenantIdAndInstitutionIdAndAcademicSessionIdAndSemesterId(
            UUID tenantId, UUID institutionId, UUID academicSessionId, UUID semesterId);
}
