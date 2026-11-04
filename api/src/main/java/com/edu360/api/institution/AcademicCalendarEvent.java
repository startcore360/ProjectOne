package com.edu360.api.institution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: a mapping of app.academic_calendar_event - "Semester dates, holidays and events
 * [CL-01..03]". CALENDAR_LOCAL_SET only ever writes the INSTITUTION / LOCAL_HOLIDAY /
 * LOCAL_EVENT corner of this table; the UNIVERSITY-level / SEMESTER_DATES corner is the separate,
 * not-yet-built CALENDAR_MASTER_SET story (per the task brief - deliberately not implemented
 * here). ck_academic_calendar_event_r2/r3 already enforce the UNIVERSITY-vs-INSTITUTION split at
 * the database level; CalendarEventService additionally rejects the wrong combination itself so
 * the caller gets a clear 400, not a raw constraint-violation 500.
 */
@Entity
@Table(schema = "app", name = "academic_calendar_event")
@Getter
@Setter
@NoArgsConstructor
public class AcademicCalendarEvent {

    @Id
    @Column(name = "academic_calendar_event_id")
    private UUID academicCalendarEventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "academic_session_id", nullable = false)
    private UUID academicSessionId;

    /** NULL for university-level rows; CALENDAR_LOCAL_SET always sets this. */
    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "source_level", nullable = false)
    private String sourceLevel;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}
