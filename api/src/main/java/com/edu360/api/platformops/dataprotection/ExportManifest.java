package com.edu360.api.platformops.dataprotection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * E5-S3: the reviewed table-to-export-group manifest for a tenant data export (CL-09).
 *
 * WHAT: every app.* table is either exported (one CSV per table, inside its group folder) or explicitly
 * excluded with a reason. Columns that hold secrets or internal locators are never exported.
 * HOW: groups follow the 14 Phase-1 headings in PLATFORM_COMPLETION_PLAN.md (E5-S3) plus a few named extra
 * groups for tenant data outside those headings. Yearly attendance partitions (attendance_entry_yYYYY) are
 * exported through their parent table, so they are excluded by pattern rather than one by one.
 * WHY: the export reads only allowlisted tables (no schema introspection at run time), and an unclassified
 * table never fails a tenant's export. Drift is caught at build time by ExportManifestDriftTest.
 * Manifest drafted in the A5 data-protection chat; reviewed and completed locally (2026-11-18).
 */
public final class ExportManifest {

    private ExportManifest() { }

    public static final Map<String, List<String>> GROUPS = groups();
    public static final Map<String, String> EXCLUDED_TABLES = excludedTables();
    public static final Map<String, Map<String, String>> EXCLUDED_COLUMNS = excludedColumns();

    /** Partition children of app.attendance_entry; their rows are exported through the parent. */
    public static final Pattern PARTITION_CHILD = Pattern.compile("attendance_entry_y\\d{4}");

    private static final Pattern SECRET_COLUMN = Pattern.compile(
            "(?i)(password|passwd|token|secret|mfa|recovery|backup_code|otp_secret|authenticator_secret|"
                    + "two_factor_secret|private_key|public_key|auth_key|api_key|client_secret|encryption_key|"
                    + "signing_key|webhook_secret|endpoint_ref|p256dh|vapid|push.*(key|endpoint)|"
                    + "identity_(provider|realm|subject)|idp_|storage_key|store_object_key|request_key|dedupe_key|"
                    + "credential_(hash|secret|token))");

    private static Map<String, List<String>> groups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("users", List.of("user_account", "user_device", "user_login_alias", "user_active_role",
                "saved_filter", "dashboard_preference"));
        groups.put("institutions", List.of("tenant_local", "institution"));
        groups.put("academic_structure", List.of("academic_session", "academic_calendar_event", "weekly_off_rule",
                "department", "program", "program_rule_set", "program_year", "program_curriculum_version", "semester",
                "school_class", "section", "subject", "syllabus", "course_offering", "subject_group",
                "subject_group_member", "subject_allocation", "mentor_assignment", "class_teacher_assignment",
                "timetable_slot", "meeting_link", "substitute_assignment"));
        groups.put("enrolments", List.of("student_profile", "student_code_sequence", "guardian_profile",
                "student_guardian_link", "parent_consent", "student_enrollment", "promotion_run", "promotion_run_item",
                "certificate_request", "admission_cycle", "admission_application", "merit_list", "merit_list_item",
                "seat_allotment", "certificate_template", "certificate_issuance", "id_card_template", "id_card_issuance", "id_card_replacement"));
        groups.put("attendance", List.of("attendance_session", "attendance_sync_conflict", "attendance_entry",
                "attendance_correction_request", "attendance_correction", "attendance_bulk_upload", "term_lock",
                "term_lock_event", "term_unlock_log", "risk_flag", "risk_remark"));
        groups.put("exams", List.of("grading_scheme_version", "grade_band", "exam_rule", "exam_pattern",
                "exam_pattern_component", "exam", "exam_schedule_item", "exam_schedule", "exam_invigilator", "exam_cycle", "exam_mark", "grade_scale", "report_card", "hall_ticket", "marks_submission",
                "marks_entry", "backlog", "supplementary_registration"));
        groups.put("results", List.of("result", "result_line", "result_publication", "result_withdrawal_request"));
        groups.put("fees", List.of("fee_head", "fee_policy", "fee_head_rule", "fee_structure", "fee_structure_line",
                "fee_structure_installment", "fee_exception", "student_fee_account", "fee_assignment",
                "student_installment", "ledger_entry", "payment", "payment_intent_item", "gateway_order",
                "gateway_transaction", "payment_gateway_event", "payment_allocation", "credit_application",
                "reconciliation_case", "receipt_sequence", "receipt", "scholarship_scheme", "scholarship_award_list",
                "scholarship_award", "concession", "late_fee_rule", "late_fee_waiver", "financial_period_lock",
                "financial_period_lock_event"));
        groups.put("leave", List.of("leave_approver_setting", "leave_type", "leave_balance", "leave_request",
                "leave_delegation"));
        groups.put("notices", List.of("notice", "notice_audience", "notice_bulletin", "notice_target", "notice_acknowledgment"));
        groups.put("complaints", List.of("complaint", "complaint_action", "disciplinary_incident", "grievance_case", "grievance_evidence", "grievance_action"));
        groups.put("notes", List.of("student_note"));
        groups.put("staff", List.of("staff_profile", "staff_code_sequence", "staff_service_record",
                "staff_assignment", "staff_document", "appraisal_cycle", "appraisal_submission", "appraisal_review"));
        groups.put("policies", List.of("role_definition", "role_action", "role_rolldown_override",
                "gba_rolldown_revocation", "tenant_role_template", "tenant_role_template_action", "tenant_step_in",
                "user_role_assignment", "delegation", "policy", "policy_acceptance", "department_document",
                "compliance_report", "accreditation_criterion", "accreditation_document",
                "accreditation_evidence_map", "governance_item", "audit_finding", "data_request",
                "data_request_event", "legal_hold", "audit_outbox", "setting_value"));
        // Tenant business data that fits none of the 14 Phase-1 headings.
        groups.put("approvals", List.of("approval_request", "approval_batch", "approval_step", "duplicate_review",
                "bulk_correction_batch", "bulk_correction_item"));
        groups.put("communication", List.of("message_thread", "message_thread_participant", "message",
                "message_attachment", "notification", "notification_delivery", "notification_delivery_attempt",
                "push_subscription", "channel_config", "sms_template", "communication_campaign", "message_template", "message_dispatch"));
        groups.put("community_events", List.of("community_post", "community_post_media", "post_comment",
                "post_like", "event", "event_rsvp"));
        groups.put("learning_content", List.of("assignment", "assignment_submission", "assignment_submission_file",
                "assignment_feedback", "homework_status", "study_material", "submission_grade", "plagiarism_log"));
        groups.put("library", List.of("library_catalog", "book_copy", "book_circulation", "library_fine", "library_fine_policy", "library_reservation"));
        groups.put("transport", List.of("vehicle", "transport_route", "route_stop", "transport_allocation"));
        groups.put("timetable_rooms", List.of("room", "online_session", "online_session_access_log", "attendance_threshold_policy", "attendance_medical_exemption"));
        groups.put("analytics", List.of("usage_metric_daily", "report_definition", "report_execution"));
        return Collections.unmodifiableMap(groups);
    }

    private static Map<String, String> excludedTables() {
        Map<String, String> excluded = new LinkedHashMap<>();
        excluded.put("user_identity_link", "Identity-provider realm and subject identifiers; IdP internals are not exported.");
        excluded.put("file_object", "File bytes are copied into documents/ with a safe documents_manifest.csv, not a CSV of storage metadata.");
        excluded.put("import_job", "Transient import execution state; the uploaded source files are in documents/.");
        excluded.put("import_row_error", "Import diagnostics can echo submitted rows; processing diagnostics, not business records.");
        excluded.put("report_export_job", "Transient report-generation state and expiring report-file lifecycle.");
        excluded.put("background_job", "Transient background queue, lease and execution state.");
        excluded.put("idempotency_key", "Request replay keys and transient request metadata.");
        excluded.put("outbox_event", "Event-delivery queue and retry state; not tenant business records.");
        return Collections.unmodifiableMap(excluded);
    }

    private static Map<String, Map<String, String>> excludedColumns() {
        Map<String, Map<String, String>> excluded = new LinkedHashMap<>();
        excluded.put("channel_config", Map.of("secret_ref", "Secret-manager reference; credential references are not disclosed."));
        excluded.put("push_subscription", Map.of("endpoint_ref", "Encrypted push endpoint and subscription capability."));
        excluded.put("audit_outbox", Map.of(
                "store_object_key", "Internal write-once audit-store locator.",
                "old_values", "Historical audit snapshots may contain sensitive values; event metadata remains exported.",
                "new_values", "Historical audit snapshots may contain sensitive values; event metadata remains exported."));
        excluded.put("payment", Map.of("request_key", "Payment request idempotency/replay key."));
        excluded.put("notification", Map.of("dedupe_key", "Internal notification deduplication key."));
        excluded.put("setting_value", Map.of("value", "Opaque JSON setting payload may contain credentials or integration secrets; metadata only."));
        return Collections.unmodifiableMap(excluded);
    }

    public static boolean isExcludedColumn(String table, String column) {
        if (column == null) return true;
        return SECRET_COLUMN.matcher(column).find()
                || EXCLUDED_COLUMNS.getOrDefault(table, Map.of()).containsKey(column);
    }

    public static boolean isExcludedTable(String table) {
        return EXCLUDED_TABLES.containsKey(table) || PARTITION_CHILD.matcher(table).matches();
    }

    public static Set<String> groupedTableNames() {
        Set<String> names = new LinkedHashSet<>();
        GROUPS.values().forEach(names::addAll);
        return Collections.unmodifiableSet(names);
    }
}
