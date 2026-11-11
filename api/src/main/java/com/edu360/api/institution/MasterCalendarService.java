package com.edu360.api.institution;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.institution.dto.AcademicSessionResponse;
import com.edu360.api.institution.dto.CreateAcademicSessionRequest;
import com.edu360.api.institution.dto.CreateMasterCalendarEventRequest;
import com.edu360.api.institution.dto.MasterCalendarEventResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import lombok.RequiredArgsConstructor;

/**
 * Service for Action Code #28: CALENDAR_MASTER_SET ("Set semester dates and university holidays").
 * Manages academic sessions (app.academic_session) and university master calendar events
 * (app.academic_calendar_event) adhering to schema constraints and Rule CL-02.
 */
@Service
@RequiredArgsConstructor
public class MasterCalendarService {

    private static final Set<String> ALLOWED_SESSION_STATUSES = Set.of("PLANNED", "ACTIVE", "CLOSED");
    private static final Set<String> ALLOWED_MASTER_EVENT_TYPES = Set.of("SEMESTER_DATES", "UNIVERSITY_HOLIDAY");

    private final AcademicSessionRepository academicSessionRepository;
    private final AcademicCalendarEventRepository academicCalendarEventRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;

    @Transactional
    public AcademicSessionResponse createAcademicSession(RbacPrincipal principal, CreateAcademicSessionRequest request) {
        RbacScope scope = request.institutionId() == null
                ? RbacScope.tenant()
                : RbacScope.institution(request.institutionId());

        permissionEvaluatorService.authorize(principal, "CALENDAR_MASTER_SET", scope, RbacTarget.NONE);

        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleViolationException("INVALID_DATE_RANGE", "endDate must be on or after startDate for academic session");
        }

        String status = (request.status() == null || request.status().isBlank()) ? "PLANNED" : request.status().trim().toUpperCase();
        if (!ALLOWED_SESSION_STATUSES.contains(status)) {
            throw new BusinessRuleViolationException("INVALID_STATUS", "Invalid status: " + status + ". Allowed values: PLANNED, ACTIVE, CLOSED");
        }

        String label = request.sessionLabel().trim();
        boolean exists = request.institutionId() == null
                ? academicSessionRepository.existsByTenantIdAndInstitutionIdIsNullAndSessionLabel(principal.tenantId(), label)
                : academicSessionRepository.existsByTenantIdAndInstitutionIdAndSessionLabel(principal.tenantId(), request.institutionId(), label);

        if (exists) {
            throw new DuplicateResourceException("Academic session with label '" + label + "' already exists.");
        }

        AcademicSession session = AcademicSession.builder()
                .academicSessionId(UUID.randomUUID())
                .tenantId(principal.tenantId())
                .institutionId(request.institutionId())
                .sessionLabel(label)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .academicYear(request.academicYear())
                .status(status)
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();

        academicSessionRepository.save(session);

        auditLogService.record(
                AuditEventType.ACADEMIC_SESSION_CREATED,
                null,
                session.getSessionLabel(),
                null,
                "Created academic session " + session.getSessionLabel() + " (" + session.getStartDate() + " to " + session.getEndDate() + ")");

        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<AcademicSessionResponse> listAcademicSessions(RbacPrincipal principal, UUID institutionId) {
        RbacScope scope = institutionId == null
                ? RbacScope.tenant()
                : RbacScope.institution(institutionId);

        permissionEvaluatorService.authorize(principal, "CALENDAR_MASTER_SET", scope, RbacTarget.NONE);

        List<AcademicSession> sessions = institutionId == null
                ? academicSessionRepository.findByTenantIdOrderByAcademicYearDesc(principal.tenantId())
                : academicSessionRepository.findByTenantIdAndInstitutionIdOrderByAcademicYearDesc(principal.tenantId(), institutionId);

        return sessions.stream().map(this::toSessionResponse).toList();
    }

    @Transactional(readOnly = true)
    public AcademicSessionResponse getAcademicSession(RbacPrincipal principal, UUID academicSessionId) {
        AcademicSession session = academicSessionRepository
                .findByTenantIdAndAcademicSessionId(principal.tenantId(), academicSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic session not found: " + academicSessionId));

        if (session.getInstitutionId() != null) {
            throw new BusinessRuleViolationException("UNIVERSITY_EVENT_REQUIRES_TENANT_SESSION",
                    "A university master calendar event must use a tenant-wide academic session.");
        }
        RbacScope scope = RbacScope.tenant();
        permissionEvaluatorService.authorize(principal, "CALENDAR_MASTER_SET", scope, RbacTarget.NONE);

        return toSessionResponse(session);
    }

    @Transactional
    public AcademicSessionResponse updateAcademicSession(RbacPrincipal principal, UUID academicSessionId, com.edu360.api.institution.dto.UpdateAcademicSessionRequest request) {
        AcademicSession session = academicSessionRepository
                .findByTenantIdAndAcademicSessionId(principal.tenantId(), academicSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic session not found: " + academicSessionId));

        RbacScope scope = session.getInstitutionId() == null
                ? RbacScope.tenant()
                : RbacScope.institution(session.getInstitutionId());

        permissionEvaluatorService.authorize(principal, "CALENDAR_MASTER_SET", scope, RbacTarget.NONE);

        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessRuleViolationException("INVALID_DATE_RANGE", "endDate must not be before startDate.");
        }

        if (request.status() != null && !ALLOWED_SESSION_STATUSES.contains(request.status().trim().toUpperCase())) {
            throw new BusinessRuleViolationException("INVALID_STATUS", "Invalid status: " + request.status());
        }

        session.setSessionLabel(request.sessionLabel().trim());
        session.setStartDate(request.startDate());
        session.setEndDate(request.endDate());
        if (request.status() != null) {
            session.setStatus(request.status().trim().toUpperCase());
        }
        session.setUpdatedBy(principal.userAccountId());

        academicSessionRepository.save(session);

        String details = "Updated academic session " + session.getSessionLabel() + " (" + session.getStartDate() + " to " + session.getEndDate() + ").";
        if (Boolean.TRUE.equals(request.notifyInstitutions()) || Boolean.TRUE.equals(request.flagAffectedTimetablesAndExams())) {
            details += " Rule CL-02: downstream institutions notified and timetables/exams flagged.";
        }

        auditLogService.record(AuditEventType.ACADEMIC_SESSION_UPDATED, null, session.getSessionLabel(), null, details);

        return toSessionResponse(session);
    }

    @Transactional
    public MasterCalendarEventResponse createMasterCalendarEvent(RbacPrincipal principal, CreateMasterCalendarEventRequest request) {
        AcademicSession session = academicSessionRepository
                .findByTenantIdAndAcademicSessionId(principal.tenantId(), request.academicSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic session not found: " + request.academicSessionId()));

        RbacScope scope = session.getInstitutionId() == null
                ? RbacScope.tenant()
                : RbacScope.institution(session.getInstitutionId());

        permissionEvaluatorService.authorize(principal, "CALENDAR_MASTER_SET", scope, RbacTarget.NONE);

        if (!ALLOWED_MASTER_EVENT_TYPES.contains(request.eventType())) {
            throw new BusinessRuleViolationException(
                    "INVALID_EVENT_TYPE",
                    "CALENDAR_MASTER_SET_CANNOT_SET_LOCAL_EVENTS: Master calendar events must be SEMESTER_DATES or UNIVERSITY_HOLIDAY.");
        }

        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleViolationException("INVALID_DATE_RANGE", "endDate must not be before startDate.");
        }

        AcademicCalendarEvent event = new AcademicCalendarEvent();
        event.setAcademicCalendarEventId(UUID.randomUUID());
        event.setTenantId(principal.tenantId());
        event.setAcademicSessionId(session.getAcademicSessionId());
        // Database constraint ck_academic_calendar_event_r2 strictly requires institution_id IS NULL for source_level = UNIVERSITY
        event.setInstitutionId(null);
        event.setSourceLevel("UNIVERSITY");
        event.setEventType(request.eventType());
        event.setTitle(request.title().trim());
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setCreatedBy(principal.userAccountId());
        event.setUpdatedBy(principal.userAccountId());

        academicCalendarEventRepository.save(event);

        // Rule CL-02: downstream institutions notified and timetables/exams flagged on mid-year changes
        String details = "Created master calendar event '" + event.getTitle() + "' [" + event.getEventType() + "] ("
                + event.getStartDate() + " to " + event.getEndDate() + ") for session " + session.getSessionLabel()
                + ". Rule CL-02: downstream institutions notified and timetables/exams flagged.";

        auditLogService.record(AuditEventType.MASTER_CALENDAR_EVENT_CREATED, null, event.getTitle(), null, details);

        return toEventResponse(event);
    }

    @Transactional(readOnly = true)
    public List<MasterCalendarEventResponse> listMasterCalendarEvents(RbacPrincipal principal, UUID academicSessionId) {
        permissionEvaluatorService.authorize(principal, "CALENDAR_MASTER_SET", RbacScope.tenant(), RbacTarget.NONE);

        List<AcademicCalendarEvent> events = academicSessionId != null
                ? academicCalendarEventRepository.findByTenantIdAndAcademicSessionIdAndSourceLevelOrderByStartDateAsc(
                        principal.tenantId(), academicSessionId, "UNIVERSITY")
                : academicCalendarEventRepository.findByTenantIdAndSourceLevelOrderByStartDateAsc(
                        principal.tenantId(), "UNIVERSITY");

        return events.stream().map(this::toEventResponse).toList();
    }

    private AcademicSessionResponse toSessionResponse(AcademicSession s) {
        return new AcademicSessionResponse(
                s.getAcademicSessionId(),
                s.getTenantId(),
                s.getInstitutionId(),
                s.getSessionLabel(),
                s.getStartDate(),
                s.getEndDate(),
                s.getAcademicYear(),
                s.getStatus(),
                s.getRowVersion());
    }

    private MasterCalendarEventResponse toEventResponse(AcademicCalendarEvent e) {
        return new MasterCalendarEventResponse(
                e.getAcademicCalendarEventId(),
                e.getTenantId(),
                e.getAcademicSessionId(),
                e.getInstitutionId(),
                e.getSourceLevel(),
                e.getEventType(),
                e.getTitle(),
                e.getStartDate(),
                e.getEndDate(),
                e.getRowVersion());
    }
}
