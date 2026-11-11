package com.edu360.api.institution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;
class CalendarDayStatusServiceTest {
 private final AcademicCalendarEventRepository events=mock(AcademicCalendarEventRepository.class);
 private final WeeklyOffRuleRepository weekly=mock(WeeklyOffRuleRepository.class);
 private final AcademicSessionRepository sessions=mock(AcademicSessionRepository.class);
 private final CalendarDayStatusService service=new CalendarDayStatusService(events,weekly,sessions);
 @AfterEach void clear(){TenantContext.clear();}
 private void scope(UUID tenant){TenantContext.set(tenant,IsolationMode.SHARED,null,"LIST",Set.of(), "PRINCIPAL_COLLEGE",ScopeType.INSTITUTION);}
 @Test void returnsLocalAndUniversityHolidayExamAndWeeklyOffForRequestedDate(){
  UUID tenant=UUID.randomUUID(),institution=UUID.randomUUID();LocalDate date=LocalDate.of(2026,11,15);scope(tenant);
  AcademicCalendarEvent local=new AcademicCalendarEvent();local.setAcademicCalendarEventId(UUID.randomUUID());local.setEventType("LOCAL_HOLIDAY");local.setTitle("Founder's Day");
  AcademicCalendarEvent university=new AcademicCalendarEvent();university.setAcademicCalendarEventId(UUID.randomUUID());university.setEventType("SEMESTER_DATES");university.setTitle("Final Exam Week");
  when(events.findByTenantIdAndInstitutionIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenant,institution,date,date)).thenReturn(List.of(local));
  when(events.findByTenantIdAndInstitutionIdIsNullAndSourceLevelAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenant,"UNIVERSITY",date,date)).thenReturn(List.of(university));
  WeeklyOffRule rule=new WeeklyOffRule();rule.setDayOfWeek((short)7);rule.setOccurrence("ALL");rule.setEffectiveFrom(date.minusYears(1));
  when(weekly.findByTenantIdAndInstitutionIdOrderByDayOfWeekAsc(tenant,institution)).thenReturn(List.of(rule));
  AcademicSession session=new AcademicSession();session.setAcademicSessionId(UUID.randomUUID());
  when(sessions.findByTenantIdAndInstitutionIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenant,institution,date,date)).thenReturn(List.of(session));
  when(sessions.findByTenantIdAndInstitutionIdIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenant,date,date)).thenReturn(List.of());
  var status=service.forDate(tenant,institution,date);
  assertThat(status.holiday()).isTrue();assertThat(status.weeklyOff()).isTrue();assertThat(status.examDay()).isTrue();
  assertThat(status.academicSessionIds()).containsExactly(session.getAcademicSessionId());assertThat(status.calendarEventIds()).hasSize(2);
 }
 @Test void appliesOddAndEvenOccurrenceToAlternateWeekends(){
  UUID tenant=UUID.randomUUID(),institution=UUID.randomUUID();scope(tenant);
  WeeklyOffRule odd=new WeeklyOffRule();odd.setDayOfWeek((short)6);odd.setOccurrence("ODD");odd.setEffectiveFrom(LocalDate.of(2026,1,1));
  when(weekly.findByTenantIdAndInstitutionIdOrderByDayOfWeekAsc(tenant,institution)).thenReturn(List.of(odd));
  for(LocalDate d:List.of(LocalDate.of(2026,11,7),LocalDate.of(2026,11,14))){
   when(events.findByTenantIdAndInstitutionIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenant,institution,d,d)).thenReturn(List.of());
   when(events.findByTenantIdAndInstitutionIdIsNullAndSourceLevelAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenant,"UNIVERSITY",d,d)).thenReturn(List.of());
   when(sessions.findByTenantIdAndInstitutionIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenant,institution,d,d)).thenReturn(List.of());
   when(sessions.findByTenantIdAndInstitutionIdIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenant,d,d)).thenReturn(List.of());
  }
  assertThat(service.forDate(tenant,institution,LocalDate.of(2026,11,7)).weeklyOff()).isTrue();
  assertThat(service.forDate(tenant,institution,LocalDate.of(2026,11,14)).weeklyOff()).isFalse();
 }
 @Test void refusesForeignTenantCalendarScope(){UUID tenant=UUID.randomUUID();scope(tenant);
  assertThatThrownBy(()->service.forDate(UUID.randomUUID(),UUID.randomUUID(),LocalDate.now())).isInstanceOf(ResourceNotFoundException.class);}
}
