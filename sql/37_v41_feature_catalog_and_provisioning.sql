-- E1-S2 / E2-S2: resumable tenant provisioning metadata and provisional entitlement catalogue.
-- Additive and repeatable. Existing D4 provisioning step codes remain valid.
-- Execution order is deliberately defined by the service, not by lexical step-code order:
--   1 P01_REGISTRY       -> P01_REGISTRY
--   2 P02_RETENTION      -> P09_RETENTION (new; P02_DATABASE remains valid)
--   3 P03_ENTITLEMENTS   -> P05_ENTITLEMENTS
--   4 P04_KEYCLOAK_REALM -> P06_IDENTITY_REALM
--   5 P05_REALM_POLICY   -> P10_REALM_POLICY (new; P03_MIGRATIONS remains valid)
--   6 P06_FIRST_ADMIN   -> P07_FIRST_ADMIN
--   7 P07_STANDARD_ROLES -> P04_ROLES_SEED

ALTER TABLE platform.tenant_provision_step
    ADD COLUMN IF NOT EXISTS started_at timestamptz,
    ADD COLUMN IF NOT EXISTS finished_at timestamptz;

COMMENT ON COLUMN platform.tenant_provision_step.started_at IS
    'Timestamp when this provisioning step most recently entered RUNNING [E1-S2].';
COMMENT ON COLUMN platform.tenant_provision_step.finished_at IS
    'Timestamp when this provisioning step most recently reached a terminal state [E1-S2].';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'platform.tenant_provision_step'::regclass
          AND conname = 'ck_tenant_provision_step_step_code'
    ) THEN
        ALTER TABLE platform.tenant_provision_step
            DROP CONSTRAINT ck_tenant_provision_step_step_code;
    END IF;

    ALTER TABLE platform.tenant_provision_step
        ADD CONSTRAINT ck_tenant_provision_step_step_code
        CHECK (step_code IN (
            'P01_REGISTRY',
            'P02_DATABASE',
            'P03_MIGRATIONS',
            'P04_ROLES_SEED',
            'P05_ENTITLEMENTS',
            'P06_IDENTITY_REALM',
            'P07_FIRST_ADMIN',
            'P08_TRIAL',
            'P09_RETENTION',
            'P10_REALM_POLICY'
        ));
END $$;

-- Feature codes are globally unique in D4, so each key is module-prefixed.
ALTER TABLE ref.feature_catalog
    ADD COLUMN IF NOT EXISTS provisional boolean NOT NULL DEFAULT true;

COMMENT ON COLUMN ref.feature_catalog.provisional IS
    'True while the feature definition remains provisional under OD-43 / PD-11.';

INSERT INTO ref.feature_catalog (feature_code, module_code, description, provisional) VALUES
    ('USERS_ACCESS_BULK_UPLOAD', 'USERS_ACCESS', 'Bulk upload', true),
    ('USERS_ACCESS_CUSTOM_ROLES', 'USERS_ACCESS', 'Custom roles', true),
    ('USERS_ACCESS_GUARDIAN_LINKS', 'USERS_ACCESS', 'Guardian links', true),
    ('USERS_ACCESS_PARENT_OTP_LOGIN', 'USERS_ACCESS', 'Parent OTP login', true),
    ('INSTITUTION_SETUP_SUBJECT_GROUPS', 'INSTITUTION_SETUP', 'Subject groups', true),
    ('INSTITUTION_SETUP_PROMOTION', 'INSTITUTION_SETUP', 'Promotion', true),
    ('INSTITUTION_SETUP_GRADING_SCHEMES', 'INSTITUTION_SETUP', 'Grading schemes', true),
    ('INSTITUTION_SETUP_WEEKLY_OFF', 'INSTITUTION_SETUP', 'Weekly off', true),
    ('TIMETABLE_ONLINE_CLASSES', 'TIMETABLE', 'Online classes', true),
    ('TIMETABLE_SYLLABUS', 'TIMETABLE', 'Syllabus', true),
    ('ATTENDANCE_CORRECTION_REQUESTS', 'ATTENDANCE', 'Correction requests', true),
    ('ATTENDANCE_BULK_UPLOAD', 'ATTENDANCE', 'Bulk upload', true),
    ('ATTENDANCE_SUBSTITUTES', 'ATTENDANCE', 'Substitutes', true),
    ('ATTENDANCE_DAILY_SMS_SUMMARY', 'ATTENDANCE', 'Daily SMS summary', true),
    ('EXAMINATIONS_HALL_TICKETS', 'EXAMINATIONS', 'Hall tickets', true),
    ('EXAMINATIONS_EXTERNAL_MARKS_UPLOAD', 'EXAMINATIONS', 'External marks upload', true),
    ('EXAMINATIONS_SUPPLEMENTARY', 'EXAMINATIONS', 'Supplementary examinations', true),
    ('RESULTS_WITHDRAWAL', 'RESULTS', 'Withdrawal', true),
    ('RESULTS_PERFORMANCE_REPORT', 'RESULTS', 'Performance report', true),
    ('ASSIGNMENTS_STUDY_MATERIAL', 'ASSIGNMENTS', 'Study material', true),
    ('ASSIGNMENTS_HOMEWORK_STATUS', 'ASSIGNMENTS', 'Homework status', true),
    ('FEES_ONLINE_PAYMENT', 'FEES', 'Online payment', true),
    ('FEES_OFFLINE_PAYMENT', 'FEES', 'Offline payment', true),
    ('FEES_SCHOLARSHIPS', 'FEES', 'Scholarships', true),
    ('FEES_CONCESSIONS', 'FEES', 'Concessions', true),
    ('FEES_LATE_FEE_RULES', 'FEES', 'Late fee rules', true),
    ('FEES_REMINDERS', 'FEES', 'Reminders', true),
    ('FEES_TALLY_EXPORT', 'FEES', 'Tally export', true),
    ('FEES_PERIOD_LOCK', 'FEES', 'Period lock', true),
    ('LEAVE_STUDENT_LEAVE', 'LEAVE', 'Student leave', true),
    ('LEAVE_STAFF_LEAVE', 'LEAVE', 'Staff leave', true),
    ('LEAVE_LEAVE_BALANCES', 'LEAVE', 'Leave balances', true),
    ('LEAVE_LOSS_OF_PAY', 'LEAVE', 'Loss of pay', true),
    ('COMMUNICATION_NOTICES', 'COMMUNICATION', 'Notices', true),
    ('COMMUNICATION_MESSAGES', 'COMMUNICATION', 'Messages', true),
    ('COMMUNICATION_CLASS_BROADCAST', 'COMMUNICATION', 'Class broadcast', true),
    ('COMMUNICATION_SMS', 'COMMUNICATION', 'SMS', true),
    ('COMMUNICATION_EMAIL', 'COMMUNICATION', 'Email', true),
    ('COMMUNICATION_PUSH', 'COMMUNICATION', 'Push notifications', true),
    ('COMMUNICATION_EMERGENCY_NOTICE', 'COMMUNICATION', 'Emergency notice', true),
    ('COMMUNITY_POSTS', 'COMMUNITY', 'Posts', true),
    ('COMMUNITY_COMMENTS', 'COMMUNITY', 'Comments', true),
    ('COMMUNITY_EVENTS', 'COMMUNITY', 'Events', true),
    ('COMMUNITY_RSVP', 'COMMUNITY', 'Event RSVP', true),
    ('COMPLAINTS_ESCALATION', 'COMPLAINTS', 'Escalation', true),
    ('STUDENT_NOTES_DISCIPLINE', 'STUDENT_NOTES', 'Discipline notes', true),
    ('STUDENT_NOTES_HEALTH', 'STUDENT_NOTES', 'Health notes', true),
    ('STUDENT_NOTES_PARENT_HEALTH_VIEW', 'STUDENT_NOTES', 'Parent health view', true),
    ('POLICIES_POLICY_ACCEPTANCE', 'POLICIES', 'Policy acceptance', true),
    ('POLICIES_DEPARTMENT_DOCUMENTS', 'POLICIES', 'Department documents', true),
    ('POLICIES_ACCREDITATION_DOCUMENTS', 'POLICIES', 'Accreditation documents', true),
    ('STAFF_RECORDS_SERVICE_RECORDS', 'STAFF_RECORDS', 'Service records', true),
    ('STAFF_RECORDS_DOCUMENT_VERIFICATION', 'STAFF_RECORDS', 'Document verification', true),
    ('RISK_ATTENDANCE_RISK', 'RISK', 'Attendance risk', true),
    ('RISK_ACADEMIC_RISK', 'RISK', 'Academic risk', true),
    ('RISK_PARENT_ALERTS', 'RISK', 'Parent alerts', true),
    ('REPORTS_FIXED_REPORTS', 'REPORTS', 'Fixed reports', true),
    ('REPORTS_BUYER_DASHBOARD', 'REPORTS', 'Buyer dashboard', true),
    ('REPORTS_STATUTORY_EXPORTS', 'REPORTS', 'Statutory exports', true),
    ('REPORTS_ID_CARD_EXPORT', 'REPORTS', 'ID card export', true),
    ('REPORTS_USAGE_ANALYTICS', 'REPORTS', 'Usage analytics', true),
    ('DATA_RECORDS_IMPORT_TEMPLATES', 'DATA_RECORDS', 'Import templates', true),
    ('DATA_RECORDS_GLOBAL_SEARCH', 'DATA_RECORDS', 'Global search', true),
    ('DATA_RECORDS_DUPLICATE_REVIEW', 'DATA_RECORDS', 'Duplicate review', true),
    ('DATA_RECORDS_BULK_CORRECTION', 'DATA_RECORDS', 'Bulk correction', true),
    ('DATA_RECORDS_CERTIFICATES', 'DATA_RECORDS', 'Certificates', true),
    ('GOVERNANCE_AUDIT_EXPORT', 'GOVERNANCE', 'Audit export', true),
    ('GOVERNANCE_DATA_REQUESTS', 'GOVERNANCE', 'Data requests', true),
    ('GOVERNANCE_CONSENT', 'GOVERNANCE', 'Consent', true),
    ('GOVERNANCE_COMPLIANCE_REPORTS', 'GOVERNANCE', 'Compliance reports', true),
    ('GOVERNANCE_RBAC_MAPPING', 'GOVERNANCE', 'RBAC mapping', true),
    ('PLATFORM_SUPPORT_ACCESS', 'PLATFORM', 'Support access', true),
    ('PLATFORM_BACKUP_STATUS', 'PLATFORM', 'Backup status', true),
    ('PAYROLL_CORE', 'PAYROLL', 'Core module entitlement', true),
    ('LIBRARY_CORE', 'LIBRARY', 'Core module entitlement', true),
    ('PLACEMENT_CORE', 'PLACEMENT', 'Core module entitlement', true),
    ('ADMISSIONS_CORE', 'ADMISSIONS', 'Core module entitlement', true),
    ('HOSTEL_CORE', 'HOSTEL', 'Core module entitlement', true),
    ('TRANSPORT_CORE', 'TRANSPORT', 'Core module entitlement', true),
    ('ALUMNI_CORE', 'ALUMNI', 'Core module entitlement', true),
    ('RESEARCH_CORE', 'RESEARCH', 'Core module entitlement', true),
    ('API_GOVERNANCE_CORE', 'API_GOVERNANCE', 'Core module entitlement', true)
ON CONFLICT (feature_code) DO UPDATE
SET module_code = EXCLUDED.module_code,
    description = EXCLUDED.description,
    provisional = true;

-- Every module except USERS_ACCESS requires USERS_ACCESS. Additional hard dependencies
-- are seeded separately; the service must block disabling a required module with dependants.
WITH dependency_seed(module_code, requires_module_code) AS (
    VALUES
        ('INSTITUTION_SETUP', 'USERS_ACCESS'),
        ('TIMETABLE', 'USERS_ACCESS'),
        ('ATTENDANCE', 'USERS_ACCESS'),
        ('EXAMINATIONS', 'USERS_ACCESS'),
        ('RESULTS', 'USERS_ACCESS'),
        ('ASSIGNMENTS', 'USERS_ACCESS'),
        ('FEES', 'USERS_ACCESS'),
        ('LEAVE', 'USERS_ACCESS'),
        ('COMMUNICATION', 'USERS_ACCESS'),
        ('COMMUNITY', 'USERS_ACCESS'),
        ('COMPLAINTS', 'USERS_ACCESS'),
        ('STUDENT_NOTES', 'USERS_ACCESS'),
        ('POLICIES', 'USERS_ACCESS'),
        ('STAFF_RECORDS', 'USERS_ACCESS'),
        ('RISK', 'USERS_ACCESS'),
        ('REPORTS', 'USERS_ACCESS'),
        ('DATA_RECORDS', 'USERS_ACCESS'),
        ('GOVERNANCE', 'USERS_ACCESS'),
        ('PLATFORM', 'USERS_ACCESS'),
        ('PAYROLL', 'USERS_ACCESS'),
        ('LIBRARY', 'USERS_ACCESS'),
        ('PLACEMENT', 'USERS_ACCESS'),
        ('ADMISSIONS', 'USERS_ACCESS'),
        ('HOSTEL', 'USERS_ACCESS'),
        ('TRANSPORT', 'USERS_ACCESS'),
        ('ALUMNI', 'USERS_ACCESS'),
        ('RESEARCH', 'USERS_ACCESS'),
        ('API_GOVERNANCE', 'USERS_ACCESS'),
        ('RESULTS', 'EXAMINATIONS'),
        ('EXAMINATIONS', 'INSTITUTION_SETUP'),
        ('ATTENDANCE', 'TIMETABLE'),
        ('TIMETABLE', 'INSTITUTION_SETUP'),
        ('ASSIGNMENTS', 'INSTITUTION_SETUP'),
        ('FEES', 'INSTITUTION_SETUP'),
        ('LEAVE', 'USERS_ACCESS'),
        ('RISK', 'ATTENDANCE')
),
missing AS (
    SELECT DISTINCT s.module_code, s.requires_module_code
    FROM dependency_seed s
    WHERE NOT EXISTS (
        SELECT 1 FROM ref.module_dependency d
        WHERE d.module_code = s.module_code
          AND d.requires_module_code = s.requires_module_code
    )
),
numbered AS (
    SELECT module_code, requires_module_code,
           row_number() OVER (ORDER BY module_code, requires_module_code) AS rn
    FROM missing
),
base AS (
    SELECT COALESCE(max(module_dependency_id), 0) AS max_id
    FROM ref.module_dependency
)
INSERT INTO ref.module_dependency (module_dependency_id, module_code, requires_module_code, kind)
SELECT base.max_id + numbered.rn, numbered.module_code, numbered.requires_module_code, 'REQUIRED'
FROM numbered CROSS JOIN base
WHERE NOT EXISTS (
    SELECT 1 FROM ref.module_dependency d
    WHERE d.module_code = numbered.module_code
      AND d.requires_module_code = numbered.requires_module_code
);

-- Required modules cannot be switched off; enforced by the entitlement command layer.
COMMENT ON TABLE ref.module_dependency IS
    'Declared module dependencies. REQUIRED edges block disabling a module while a dependent module remains enabled [E2-S2, PD-11].';
