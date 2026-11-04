package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.WeeklyOffRuleRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@ExtendWith(MockitoExtension.class)
class WeeklyOffRuleServiceTest {

    @Mock private WeeklyOffRuleRepository weeklyOffRuleRepository;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();

    private final RbacPrincipal principal = new RbacPrincipal(TENANT_ID, USER_ID);

    private WeeklyOffRuleService service() {
        return new WeeklyOffRuleService(weeklyOffRuleRepository, permissionEvaluatorService);
    }

    @Test
    void setRule_savesAnAlternateSaturdayRuleWhenAuthorized() {
        when(weeklyOffRuleRepository.save(any(WeeklyOffRule.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new WeeklyOffRuleRequest(INSTITUTION_ID, (short) 6, "ODD", LocalDate.of(2026, 6, 1), null);
        var response = service().setRule(principal, request);

        assertThat(response.dayOfWeek()).isEqualTo((short) 6);
        assertThat(response.occurrence()).isEqualTo("ODD");
        org.mockito.Mockito.verify(permissionEvaluatorService).authorize(
                principal, "WEEKLY_OFF_SET", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);
    }

    @Test
    void setRule_defaultsOccurrenceToAllWhenNotGiven() {
        when(weeklyOffRuleRepository.save(any(WeeklyOffRule.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new WeeklyOffRuleRequest(INSTITUTION_ID, (short) 7, null, LocalDate.of(2026, 6, 1), null);
        var response = service().setRule(principal, request);

        assertThat(response.occurrence()).isEqualTo("ALL");
    }

    @Test
    void setRule_rejectsAnOutOfRangeDayOfWeek() {
        var request = new WeeklyOffRuleRequest(INSTITUTION_ID, (short) 8, "ALL", LocalDate.of(2026, 6, 1), null);

        assertThatThrownBy(() -> service().setRule(principal, request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setRule_deniesWhenEvaluatorDenies() {
        org.mockito.Mockito.doThrow(new UnauthorizedException("denied"))
                .when(permissionEvaluatorService)
                .authorize(principal, "WEEKLY_OFF_SET", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        var request = new WeeklyOffRuleRequest(INSTITUTION_ID, (short) 1, "ALL", LocalDate.of(2026, 6, 1), null);

        assertThatThrownBy(() -> service().setRule(principal, request)).isInstanceOf(UnauthorizedException.class);
        org.mockito.Mockito.verify(weeklyOffRuleRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void listRules_authorizesThenReturnsInstitutionRows() {
        WeeklyOffRule rule = new WeeklyOffRule();
        rule.setWeeklyOffRuleId(UUID.randomUUID());
        rule.setInstitutionId(INSTITUTION_ID);
        rule.setDayOfWeek((short) 7);
        rule.setOccurrence("ALL");
        rule.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        when(weeklyOffRuleRepository.findByTenantIdAndInstitutionIdOrderByDayOfWeekAsc(TENANT_ID, INSTITUTION_ID))
                .thenReturn(List.of(rule));

        var result = service().listRules(principal, INSTITUTION_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dayOfWeek()).isEqualTo((short) 7);
    }
}
