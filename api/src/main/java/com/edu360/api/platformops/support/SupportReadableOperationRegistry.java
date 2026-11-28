package com.edu360.api.platformops.support;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** OpenAPI x-support-readable=true operation allow-list, generated from the V4.0 OpenAPI contract. */
public final class SupportReadableOperationRegistry {
    private static final PathPatternParser PARSER = new PathPatternParser();
    private record Entry(PathPattern path, Set<String> actionCodes) {}
    private static final List<Entry> ENTRIES = List.of(
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{id}"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/hierarchy"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/departments"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/departments/{id}"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/programs"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/programs/{id}"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/programs/{id}/years"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/programs/{id}/curriculum-versions"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/program-years/{id}/semesters"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/classes"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/classes/{id}"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/classes/{id}/sections"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/sections"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/sections/{id}"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/subjects"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/subjects/{id}"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/course-offerings"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/course-offerings/{id}"), Set.of("STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/academic-sessions"), Set.of("CALENDAR_MASTER_SET", "CALENDAR_LOCAL_SET", "STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/academic-sessions/{id}"), Set.of("CALENDAR_MASTER_SET", "CALENDAR_LOCAL_SET", "STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/program-rule-sets"), Set.of("PROGRAM_RULES_SET", "STRUCTURE_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/weekly-off-rules"), Set.of("WEEKLY_OFF_SET", "TIMETABLE_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/institutions/{institutionId}/calendar-events"), Set.of("CALENDAR_MASTER_SET", "CALENDAR_LOCAL_SET", "TIMETABLE_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/users/{id}/role-assignments"), Set.of("ROLE_ASSIGN", "ROLE_ASSIGN_NON_SENSITIVE", "USER_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/roles"), Set.of("ROLE_CUSTOM_MANAGE", "ROLE_ASSIGN", "ROLE_ASSIGN_NON_SENSITIVE", "RBAC_MAPPING_CHECK", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/roles/{id}"), Set.of("ROLE_CUSTOM_MANAGE", "ROLE_ASSIGN", "ROLE_ASSIGN_NON_SENSITIVE", "RBAC_MAPPING_CHECK", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/delegations"), Set.of("APPROVAL_DELEGATE")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/approvals/inbox"), Set.of("GUARDIAN_CHANGE_APPROVE", "INSTITUTION_ACTIVATE", "INSTITUTION_BLOCK_APPROVE", "PROMOTION_APPROVE", "ATTENDANCE_CORRECTION_APPROVE", "ATTENDANCE_BULK_UPLOAD_APPROVE", "EXAM_SCHEDULE_APPROVE", "MARKS_APPROVE_HOD", "MARKS_REVIEW_CLASS_TEACHER", "MARKS_APPROVE_PRINCIPAL", "RESULT_WITHDRAW_APPROVE", "FEE_STRUCTURE_APPROVE", "FEE_EXCEPTION_APPROVE_FIRST", "FEE_EXCEPTION_APPROVE_PRINCIPAL", "SCHOLARSHIP_LIST_APPROVE", "CONCESSION_APPROVE", "LATE_FEE_WAIVER_APPROVE", "RECEIPT_CANCEL_APPROVE", "PERIOD_UNLOCK_APPROVE", "STUDENT_LEAVE_APPROVE_MENTOR", "STUDENT_LEAVE_APPROVE_HOD", "STUDENT_LEAVE_APPROVE_CT", "STUDENT_LEAVE_APPROVE_PRINCIPAL", "STAFF_LEAVE_RECOMMEND_HOD", "STAFF_LEAVE_VERIFY_HR", "STAFF_LEAVE_APPROVE_PRINCIPAL", "STAFF_LEAVE_APPROVE_REGISTRAR", "PRINCIPAL_LEAVE_APPROVE", "SENIOR_LEAVE_APPROVE", "NOTICE_APPROVE", "COMMUNITY_POST_APPROVE", "EVENT_APPROVE", "CERTIFICATE_APPROVE", "GOVERNANCE_ITEM_APPROVE", "UNIVERSITY_POLICY_APPROVE")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/approval-requests/{id}"), Set.of("GUARDIAN_CHANGE_APPROVE", "INSTITUTION_ACTIVATE", "INSTITUTION_BLOCK_APPROVE", "PROMOTION_APPROVE", "ATTENDANCE_CORRECTION_APPROVE", "ATTENDANCE_BULK_UPLOAD_APPROVE", "EXAM_SCHEDULE_APPROVE", "MARKS_APPROVE_HOD", "MARKS_REVIEW_CLASS_TEACHER", "MARKS_APPROVE_PRINCIPAL", "RESULT_WITHDRAW_APPROVE", "FEE_STRUCTURE_APPROVE", "FEE_EXCEPTION_APPROVE_FIRST", "FEE_EXCEPTION_APPROVE_PRINCIPAL", "SCHOLARSHIP_LIST_APPROVE", "CONCESSION_APPROVE", "LATE_FEE_WAIVER_APPROVE", "RECEIPT_CANCEL_APPROVE", "PERIOD_UNLOCK_APPROVE", "STUDENT_LEAVE_APPROVE_MENTOR", "STUDENT_LEAVE_APPROVE_HOD", "STUDENT_LEAVE_APPROVE_CT", "STUDENT_LEAVE_APPROVE_PRINCIPAL", "STAFF_LEAVE_RECOMMEND_HOD", "STAFF_LEAVE_VERIFY_HR", "STAFF_LEAVE_APPROVE_PRINCIPAL", "STAFF_LEAVE_APPROVE_REGISTRAR", "PRINCIPAL_LEAVE_APPROVE", "SENIOR_LEAVE_APPROVE", "NOTICE_APPROVE", "COMMUNITY_POST_APPROVE", "EVENT_APPROVE", "CERTIFICATE_APPROVE", "GOVERNANCE_ITEM_APPROVE", "UNIVERSITY_POLICY_APPROVE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/go-live-check"), Set.of("TENANT_TOP_USERS_CREATE", "PRINCIPAL_LEAVE_APPROVER_SET", "VC_LEAVE_APPROVER_SET")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/departments"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/departments/{id}"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/programs"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/programs/{id}"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/program-years"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/program-years/{id}"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/semesters"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/semesters/{id}"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/classes"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/classes/{id}"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/sections"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/sections/{id}"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/subjects"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/subjects/{id}"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/course-offerings"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/course-offerings/{id}"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/subject-groups"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/subject-groups/{id}"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/subject-group-members"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/subject-group-members/{id}"), Set.of("STRUCTURE_MANAGE", "TIMETABLE_BUILD", "SUBJECT_ALLOCATE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/program-rule-sets"), Set.of("PROGRAM_RULES_SET", "STRUCTURE_MANAGE", "EXAM_RULES_SET", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/program-rule-sets/{id}"), Set.of("PROGRAM_RULES_SET", "STRUCTURE_MANAGE", "EXAM_RULES_SET", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/academic-sessions"), Set.of("CALENDAR_MASTER_SET", "CALENDAR_LOCAL_SET", "STRUCTURE_MANAGE", "TIMETABLE_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/academic-sessions/{id}"), Set.of("CALENDAR_MASTER_SET", "CALENDAR_LOCAL_SET", "STRUCTURE_MANAGE", "TIMETABLE_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/calendar-events"), Set.of("CALENDAR_MASTER_SET", "CALENDAR_LOCAL_SET", "TIMETABLE_VIEW", "EXAM_SCHEDULE_VIEW_OWN", "ATTENDANCE_VIEW_OWN", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/weekly-off-rules"), Set.of("WEEKLY_OFF_SET", "TIMETABLE_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/grading-schemes"), Set.of("GRADING_SCHEME_SET", "EXAM_PATTERN_SET", "EXAM_RULES_SET", "RESULT_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/subject-allocations"), Set.of("SUBJECT_ALLOCATE", "TIMETABLE_BUILD", "TIMETABLE_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/promotion-runs/{id}"), Set.of("PROMOTION_RUN", "PROMOTION_APPROVE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/term-locks"), Set.of("TERM_LOCK", "TERM_UNLOCK", "ATTENDANCE_VIEW", "EXAM_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/timetable-slots"), Set.of("TIMETABLE_VIEW", "TIMETABLE_BUILD", "ONLINE_PERIOD_SET", "EXAM_SCHEDULE_VIEW_OWN", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/syllabi"), Set.of("SYLLABUS_VIEW", "SYLLABUS_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/syllabi/{id}"), Set.of("SYLLABUS_VIEW", "SYLLABUS_MANAGE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/exam-rules"), Set.of("EXAM_RULES_SET", "EXAM_PATTERN_SET", "EXAM_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/exam-rules/{id}"), Set.of("EXAM_RULES_SET", "EXAM_PATTERN_SET", "EXAM_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/exam-patterns"), Set.of("EXAM_PATTERN_SET", "EXAM_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/exams"), Set.of("EXAM_VIEW", "EXAM_SCHEDULE_UNIVERSITY", "EXAM_SCHEDULE_INTERNAL", "EXAM_SCHEDULE_VIEW_OWN", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/me/exam-schedule"), Set.of("EXAM_SCHEDULE_VIEW_OWN")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/assignments"), Set.of("ASSIGNMENT_CREATE", "ASSIGNMENT_VIEW_OWN", "HOMEWORK_COMPLIANCE_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/study-materials"), Set.of("STUDY_MATERIAL_UPLOAD", "ASSIGNMENT_VIEW_OWN", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/fee-heads"), Set.of("FEE_POLICY_SET", "FEE_POLICY_DATA_ENTER", "FEE_STRUCTURE_CREATE", "LEDGER_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/fee-heads/{id}"), Set.of("FEE_POLICY_SET", "FEE_POLICY_DATA_ENTER", "FEE_STRUCTURE_CREATE", "LEDGER_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/fee-policies"), Set.of("FEE_POLICY_SET", "FEE_POLICY_DATA_ENTER", "FEE_STRUCTURE_CREATE", "LEDGER_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/fee-structures"), Set.of("FEE_STRUCTURE_CREATE", "FEE_STRUCTURE_APPROVE", "LEDGER_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/late-fee-rules"), Set.of("LATE_FEE_RULES_SET", "LEDGER_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/late-fee-rules/{id}"), Set.of("LATE_FEE_RULES_SET", "LEDGER_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/period-locks"), Set.of("PERIOD_LOCK", "PERIOD_UNLOCK_REQUEST", "PERIOD_UNLOCK_APPROVE", "LEDGER_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/leave-types"), Set.of("LEAVE_TYPES_SET", "STAFF_LEAVE_APPLY", "STUDENT_LEAVE_APPLY", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/leave-types/{id}"), Set.of("LEAVE_TYPES_SET", "STAFF_LEAVE_APPLY", "STUDENT_LEAVE_APPLY", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/notices"), Set.of("NOTICE_PUBLISH_INSTITUTION", "NOTICE_PUBLISH_UNIVERSITY", "NOTICE_PUBLISH_CLASS_DEPT", "NOTICE_SUBMIT", "NOTICE_APPROVE", "SELF_SERVICE_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/delivery-failures"), Set.of("DELIVERY_FAILURES_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/sms-templates"), Set.of("SMS_EMAIL_SETUP", "INTEGRATION_SETTINGS_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/community-posts"), Set.of("COMMUNITY_LIKE_COMMENT", "COMMUNITY_POST_DIRECT", "COMMUNITY_POST_SUBMIT", "COMMUNITY_POST_APPROVE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/events"), Set.of("EVENT_RSVP", "EVENT_PROPOSE", "EVENT_APPROVE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/policies"), Set.of("POLICY_DRAFT", "POLICY_PUBLISH", "POLICY_ACCEPT", "UNIVERSITY_POLICY_APPROVE", "GOVERNANCE_ITEM_APPROVE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/department-documents"), Set.of("DEPT_DOCUMENT_UPLOAD", "ACCREDITATION_EVIDENCE_MAP", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/reports"), Set.of("REPORTS_VIEW", "LEDGER_VIEW", "STATUTORY_EXPORT", "PLATFORM_REPORTS_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/reports/{reportCode}"), Set.of("REPORTS_VIEW", "LEDGER_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/dashboards/buyer"), Set.of("BUYER_DASHBOARD_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/usage-analytics"), Set.of("USAGE_ANALYTICS_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/oversight/summary"), Set.of("TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/me/saved-filters"), Set.of("REPORTS_VIEW", "LEDGER_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/jobs/{jobId}"), Set.of("DATA_IMPORT", "REPORTS_VIEW", "HALL_TICKET_GENERATE", "LEAVE_BALANCE_CREDIT", "SCHOLARSHIP_MANAGE", "FEE_REMINDER_SEND", "PROMOTION_RUN", "STATUTORY_EXPORT", "ID_CARD_EXPORT", "TALLY_EXPORT", "MARKS_EXTERNAL_UPLOAD", "ATTENDANCE_BULK_UPLOAD", "USER_BULK_UPLOAD", "AUDIT_LOG_EXPORT", "RBAC_MAPPING_CHECK", "BULK_CORRECT")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/files/{id}/download"), Set.of("STUDY_MATERIAL_UPLOAD", "ASSIGNMENT_VIEW_OWN", "MESSAGE_SEND", "STAFF_RECORD_VIEW", "POLICY_ACCEPT", "REPORTS_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/audit-findings"), Set.of("AUDIT_FINDING_RECORD", "AUDIT_LOG_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/audit-findings/{id}"), Set.of("AUDIT_FINDING_RECORD", "AUDIT_LOG_VIEW", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/compliance-reports"), Set.of("COMPLIANCE_REPORT_MANAGE", "GOVERNANCE_ITEM_APPROVE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/accreditation-documents"), Set.of("ACCREDITATION_EVIDENCE_MAP", "GOVERNANCE_ITEM_APPROVE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/governance-items"), Set.of("GOVERNANCE_ITEM_APPROVE", "TENANT_OVERSIGHT_VIEW")),
        new Entry(PARSER.parse("/v1/tenants/{tenantId}/backup-status"), Set.of("BACKUP_STATUS_VIEW"))
    );

    private SupportReadableOperationRegistry() {}

    public static Optional<Set<String>> actionCodes(String method, String requestPath) {
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) return Optional.empty();
        String openApiPath = requestPath;
        if (openApiPath != null) {
            if (openApiPath.startsWith("/api/")) {
                openApiPath = "/v1/" + openApiPath.substring(5);
            } else if (openApiPath.startsWith("/tenants/")) {
                openApiPath = "/v1" + openApiPath;
            }
        }
        if (openApiPath == null) return Optional.empty();
        PathContainer path = PathContainer.parsePath(openApiPath);
        return ENTRIES.stream().filter(entry -> entry.path().matches(path))
                .map(Entry::actionCodes).findFirst();
    }
}
