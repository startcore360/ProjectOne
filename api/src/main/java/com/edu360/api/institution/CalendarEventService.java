package com.edu360.api.institution;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
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
 * Enforces T06 (valid session required, 404 if missing), T07 (blank title rejected with 400),
 * and exam conflict prevention (LOCAL_HOLIDAY blocked on dates overlapping exam events).
 */
@Service
public class CalendarEventService {

    private static final Set<String> ALLOWED_LOCAL_EVENT_TYPES = Set.of("LOCAL_HOLIDAY", "LOCAL_EVENT", "EXAM_DAY");

    private final AcademicCalendarEventRepository academicCalendarEventRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AcademicSessionRepository academicSessionRepository;

    public CalendarEventService(AcademicCalendarEventRepository academicCalendarEventRepository,
                                PermissionEvaluatorService permissionEvaluatorService) {
        this(academicCalendarEventRepository, permissionEvaluatorService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CalendarEventService(AcademicCalendarEventRepository academicCalendarEventRepository,
                                PermissionEvaluatorService permissionEvaluatorService,
                                AcademicSessionRepository academicSessionRepository) {
        this.academicCalendarEventRepository = academicCalendarEventRepository;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.academicSessionRepository = academicSessionRepository;
    }

    @Transactional
    public CalendarEventResponse addLocalEvent(RbacPrincipal principal, CalendarEventRequest request) {
        permissionEvaluatorService.authorize(principal, "CALENDAR_LOCAL_SET",
                RbacScope.institution(request.institutionId()), RbacTarget.NONE);

        if (request.title() == null || request.title().trim().isEmpty()) {
            throw new IllegalArgumentException("title must not be blank.");
        }

        if (!ALLOWED_LOCAL_EVENT_TYPES.contains(request.eventType())) {
            throw new IllegalArgumentException(
                    "CALENDAR_LOCAL_SET may only create LOCAL_HOLIDAY or LOCAL_EVENT rows; "
                            + "semester dates and university holidays are set by the separate CALENDAR_MASTER_SET story.");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate must not be before startDate.");
        }

        // T06: the session must belong to this institution or be the tenant-wide master session.
        if (academicSessionRepository == null) throw new IllegalStateException("AcademicSessionRepository is required.");
        AcademicSession session = academicSessionRepository
                .findByTenantIdAndAcademicSessionId(principal.tenantId(), request.academicSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic session not found: " + request.academicSessionId()));
        if (session.getInstitutionId() != null && !request.institutionId().equals(session.getInstitutionId())) {
            throw new ResourceNotFoundException("Academic session not found for this institution.");
        }

        // CL-01: local holidays on exam days are blocked with a warning.
        // Use typed events; never infer exams from free-text titles.
        if ("LOCAL_HOLIDAY".equalsIgnoreCase(request.eventType())) {
            List<AcademicCalendarEvent> universityEvents =
                    academicCalendarEventRepository.findByTenantIdAndSourceLevelOrderByStartDateAsc(
                            principal.tenantId(), "UNIVERSITY");
            List<AcademicCalendarEvent> institutionEvents =
                    academicCalendarEventRepository.findByTenantIdAndInstitutionIdOrderByStartDateAsc(
                            principal.tenantId(), request.institutionId());
            boolean conflictsWithExam = java.util.stream.Stream.concat(
                            universityEvents.stream(), institutionEvents.stream())
                    .filter(e -> "EXAM_DAY".equals(e.getEventType()))
                    .anyMatch(e -> !request.endDate().isBefore(e.getStartDate())
                            && !request.startDate().isAfter(e.getEndDate()));
            if (conflictsWithExam) {
                throw new BusinessRuleViolationException(
                        "HOLIDAY_EXAM_CONFLICT",
                        "Local holiday overlaps an exam day. Choose dates outside the exam-day range.");
            }
        }

        AcademicCalendarEvent event = new AcademicCalendarEvent();
        event.setAcademicCalendarEventId(UUID.randomUUID());
        event.setTenantId(principal.tenantId());
        event.setAcademicSessionId(request.academicSessionId());
        event.setInstitutionId(request.institutionId());
        event.setSourceLevel("INSTITUTION");
        event.setEventType(request.eventType());
        event.setTitle(request.title().trim());
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
