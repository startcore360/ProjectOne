package com.edu360.api.institution;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyOffRuleRepository extends JpaRepository<WeeklyOffRule, UUID> {

    List<WeeklyOffRule> findByTenantIdAndInstitutionIdOrderByDayOfWeekAsc(UUID tenantId, UUID institutionId);
}
