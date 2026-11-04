package com.edu360.api.institution;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.institution.dto.CalendarEventRequest;
import com.edu360.api.institution.dto.CalendarEventResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import lombok.RequiredArgsConstructor;

/**
 * WHAT: CALENDAR_LOCAL_SET ("Add local holidays and events") - College Admin, School Admin; own
 * institution. Writes/reads app.academic_calendar_event rows pinned to
 * source_level = 'INSTITUTION' and event_type in ('LOCAL_HOLIDAY','LOCAL_EVENT') only.
 *
 * WHY "Never allowed: cannot change university-level semester dates" is enforced here even
 * though ck_academic_calendar_event_r2/r3 already forbid it at the database level: a caller who
 * tried SEMESTER_DATES/UNIVERSITY_HOLIDAY would otherwise get a raw constraint-violation 500
 * (GlobalExceptionHandler's catch-all) instead of a clear 400 - this rejects it before the
 * insert, same intent as the DB check, friendlier failure.
 *
 * institutionId comes from the request body (there is no institutionId in the
 * /api/tenants/{tenantId}/calendar-events path), the same pattern UserCreationController uses
 * for USER_CREATE's scope.
 */
@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private static final Set<String> ALLOWED_LOCAL_EVENT_TYPES = Set.of("LOCAL_HOLIDAY", "LOCAL_EVENT");

    private final AcademicCalendarEventRepository academicCalendarEventRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;

    @Transactional
    public CalendarEventResponse addLocalEvent(RbacPrincipal principal, CalendarEventRequest request) {
        permissionEvaluatorService.authorize(principal, "CALENDAR_LOCAL_SET",
                RbacScope.institution(request.institutionId()), RbacTarget.NONE);

        if (!ALLOWED_LOCAL_EVENT_TYPES.contains(request.eventType())) {
            throw new IllegalArgumentException(
                    "CALENDAR_LOCAL_SET may only create LOCAL_HOLIDAY or LOCAL_EVENT rows; "
                            + "semester dates and university holidays are set by the separate CALENDAR_MASTER_SET story.");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate must not be before startDate.");
        }

        AcademicCalendarEvent event = new AcademicCalendarEvent();
        event.setAcademicCalendarEventId(UUID.randomUUID());
        event.setTenantId(principal.tenantId());
        event.setAcademicSessionId(request.academicSessionId());
        event.setInstitutionId(request.institutionId());
        event.setSourceLevel("INSTITUTION");
        event.setEventType(request.eventType());
        event.setTitle(request.title());
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setCreatedBy(principal.userAccountId());
        event.setUpdatedBy(principal.userAccountId());
        academicCalendarEventRepository.save(event);

        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> listLocalEvents(RbacPrincipal principal, UUID institutionId) {
        permissionEvaluatorService.authorize(principal, "CALENDAR_LOCAL_SET",
                RbacScope.institution(institutionId), RbacTarget.NONE);

        return academicCalendarEventRepository
                .findByTenantIdAndInstitutionIdOrderByStartDateAsc(principal.tenantId(), institutionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private CalendarEventResponse toResponse(AcademicCalendarEvent event) {
        return new CalendarEventResponse(
                event.getAcademicCalendarEventId(),
                event.getInstitutionId(),
                event.getAcademicSessionId(),
                event.getSourceLevel(),
                event.getEventType(),
                event.getTitle(),
                event.getStartDate(),
                event.getEndDate());
    }
}
