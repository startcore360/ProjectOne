package com.edu360.api.institution;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.institution.dto.WeeklyOffRuleRequest;
import com.edu360.api.institution.dto.WeeklyOffRuleResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import lombok.RequiredArgsConstructor;

/**
 * WHAT: WEEKLY_OFF_SET ("Set the weekly-off pattern") - College Admin, School Admin; own
 * institution. Writes/reads app.weekly_off_rule - e.g. Sunday ALL + alternate Saturdays (ODD/EVEN)
 * [CL-03]. institutionId comes from the request body, same reasoning as CalendarEventService.
 */
@Service
@RequiredArgsConstructor
public class WeeklyOffRuleService {

    private final WeeklyOffRuleRepository weeklyOffRuleRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;

    @Transactional
    public WeeklyOffRuleResponse setRule(RbacPrincipal principal, WeeklyOffRuleRequest request) {
        permissionEvaluatorService.authorize(principal, "WEEKLY_OFF_SET",
                RbacScope.institution(request.institutionId()), RbacTarget.NONE);

        if (request.dayOfWeek() < 1 || request.dayOfWeek() > 7) {
            throw new IllegalArgumentException("dayOfWeek must be between 1 (Monday) and 7 (Sunday).");
        }
        String occurrence = request.occurrence() == null ? "ALL" : request.occurrence();
        if (!occurrence.equals("ALL") && !occurrence.equals("ODD") && !occurrence.equals("EVEN")) {
            throw new IllegalArgumentException("occurrence must be ALL, ODD or EVEN.");
        }
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new IllegalArgumentException("effectiveTo must not be before effectiveFrom.");
        }

        WeeklyOffRule rule = new WeeklyOffRule();
        rule.setWeeklyOffRuleId(UUID.randomUUID());
        rule.setTenantId(principal.tenantId());
        rule.setInstitutionId(request.institutionId());
        rule.setDayOfWeek(request.dayOfWeek());
        rule.setOccurrence(occurrence);
        rule.setEffectiveFrom(request.effectiveFrom());
        rule.setEffectiveTo(request.effectiveTo());
        rule.setCreatedBy(principal.userAccountId());
        rule.setUpdatedBy(principal.userAccountId());
        weeklyOffRuleRepository.save(rule);

        return toResponse(rule);
    }

    @Transactional(readOnly = true)
    public List<WeeklyOffRuleResponse> listRules(RbacPrincipal principal, UUID institutionId) {
        permissionEvaluatorService.authorize(principal, "WEEKLY_OFF_SET",
                RbacScope.institution(institutionId), RbacTarget.NONE);

        return weeklyOffRuleRepository
                .findByTenantIdAndInstitutionIdOrderByDayOfWeekAsc(principal.tenantId(), institutionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private WeeklyOffRuleResponse toResponse(WeeklyOffRule rule) {
        return new WeeklyOffRuleResponse(
                rule.getWeeklyOffRuleId(),
                rule.getInstitutionId(),
                rule.getDayOfWeek(),
                rule.getOccurrence(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo());
    }
}
