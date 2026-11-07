package com.edu360.api.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findByTenantIdAndSubjectId(UUID tenantId, UUID subjectId);

    List<Subject> findByTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);

    boolean existsByTenantIdAndInstitutionIdAndSubjectCode(UUID tenantId, UUID institutionId, String subjectCode);
}
