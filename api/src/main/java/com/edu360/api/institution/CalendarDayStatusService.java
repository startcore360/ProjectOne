package com.edu360.api.institution;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.tenant.TenantContext;
/** Date-scoped calendar facts consumed by attendance and timetable rules. */
@Service
public class CalendarDayStatusService {
 private static final Set<String> HOLIDAY_TYPES=Set.of("LOCAL_HOLIDAY","UNIVERSITY_HOLIDAY");
 private final AcademicCalendarEventRepository eventRepository;
 private final WeeklyOffRuleRepository weeklyOffRuleRepository;
 private final AcademicSessionRepository academicSessionRepository;
 public CalendarDayStatusService(AcademicCalendarEventRepository events,WeeklyOffRuleRepository weeklyOffs,AcademicSessionRepository sessions){
  this.eventRepository=events;this.weeklyOffRuleRepository=weeklyOffs;this.academicSessionRepository=sessions;
 }
 public record CalendarDayStatus(UUID institutionId,LocalDate date,List<UUID> academicSessionIds,
  boolean holiday,boolean weeklyOff,boolean examDay,List<UUID> calendarEventIds,List<String> eventTypes){}
 @Transactional(readOnly=true)
 public CalendarDayStatus forDate(UUID tenantId,UUID institutionId,LocalDate date){
  if(tenantId==null||institutionId==null||date==null||!tenantId.equals(TenantContext.currentTenantId()))
   throw new ResourceNotFoundException("Calendar scope not found.");
  List<AcademicCalendarEvent> local=eventRepository.findByTenantIdAndInstitutionIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenantId,institutionId,date,date);
  List<AcademicCalendarEvent> university=eventRepository.findByTenantIdAndInstitutionIdIsNullAndSourceLevelAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenantId,"UNIVERSITY",date,date);
  List<AcademicCalendarEvent> events=java.util.stream.Stream.concat(local.stream(),university.stream()).toList();
  boolean holiday=events.stream().anyMatch(e->HOLIDAY_TYPES.contains(e.getEventType()));
  // The current schema has no EXAM_DAY event type; the committed calendar rule identifies exam ranges by title.
  boolean examDay=events.stream().anyMatch(e->e.getTitle()!=null&&e.getTitle().toUpperCase(Locale.ROOT).contains("EXAM"));
  int day=date.getDayOfWeek().getValue(),occurrence=(date.getDayOfMonth()-1)/7+1;
  boolean weeklyOff=weeklyOffRuleRepository.findByTenantIdAndInstitutionIdOrderByDayOfWeekAsc(tenantId,institutionId).stream()
   .filter(r->r.getDayOfWeek()!=null&&r.getDayOfWeek()==day)
   .filter(r->r.getEffectiveFrom()!=null&&!r.getEffectiveFrom().isAfter(date))
   .filter(r->r.getEffectiveTo()==null||!r.getEffectiveTo().isBefore(date))
   .anyMatch(r->"ALL".equals(r.getOccurrence())||("ODD".equals(r.getOccurrence())&&occurrence%2==1)||("EVEN".equals(r.getOccurrence())&&occurrence%2==0));
  List<UUID> sessions=java.util.stream.Stream.concat(
   academicSessionRepository.findByTenantIdAndInstitutionIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenantId,institutionId,date,date).stream(),
   academicSessionRepository.findByTenantIdAndInstitutionIdIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenantId,date,date).stream())
   .map(AcademicSession::getAcademicSessionId).distinct().toList();
  return new CalendarDayStatus(institutionId,date,sessions,holiday,weeklyOff,examDay,
   events.stream().map(AcademicCalendarEvent::getAcademicCalendarEventId).toList(),
   events.stream().map(AcademicCalendarEvent::getEventType).distinct().sorted().toList());
 }
}
