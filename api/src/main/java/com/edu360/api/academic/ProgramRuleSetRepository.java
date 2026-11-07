package com.edu360.api.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramRuleSetRepository extends JpaRepository<ProgramRuleSet, ProgramRuleSetId> {
    List<ProgramRuleSet> findByIdTenantId(UUID tenantId);
    List<ProgramRuleSet> findByIdTenantIdAndInstitutionId(UUID tenantId, UUID institutionId);
    Optional<ProgramRuleSet> findByIdTenantIdAndIdProgramRuleSetId(UUID tenantId, UUID ruleSetId);
}
