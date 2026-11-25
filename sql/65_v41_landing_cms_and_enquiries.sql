BEGIN;

ALTER TABLE platform.tenant
    ADD COLUMN IF NOT EXISTS listed_in_directory boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS platform.landing_media (
    media_id uuid PRIMARY KEY,
    media_kind text NOT NULL,
    file_name text NOT NULL,
    content_type text NOT NULL,
    byte_size bigint NOT NULL,
    sha256 text NOT NULL,
    scan_status text NOT NULL,
    status text NOT NULL DEFAULT 'ACTIVE',
    content bytea NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_landing_media_kind CHECK (media_kind IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_landing_media_size CHECK (byte_size > 0),
    CONSTRAINT ck_landing_media_scan CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'SCAN_FAILED')),
    CONSTRAINT ck_landing_media_status CHECK (status IN ('ACTIVE', 'QUARANTINED', 'DELETED'))
);

CREATE TABLE IF NOT EXISTS platform.landing_page_config (
    config_id uuid PRIMARY KEY,
    hero_eyebrow text NOT NULL,
    hero_title text NOT NULL,
    hero_subtitle text NOT NULL,
    hero_note text NOT NULL,
    trust_label text NOT NULL,
    why_choose_json jsonb NOT NULL,
    role_benefits_json jsonb NOT NULL,
    solutions_json jsonb NOT NULL,
    steps_json jsonb NOT NULL,
    contact_email text NOT NULL,
    footer_tagline text NOT NULL,
    hero_light_media_id uuid REFERENCES platform.landing_media(media_id) ON DELETE RESTRICT,
    hero_dark_media_id uuid REFERENCES platform.landing_media(media_id) ON DELETE RESTRICT,
    showcase_light_media_id uuid REFERENCES platform.landing_media(media_id) ON DELETE RESTRICT,
    showcase_dark_media_id uuid REFERENCES platform.landing_media(media_id) ON DELETE RESTRICT,
    row_version integer NOT NULL DEFAULT 1,
    updated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_landing_config_version CHECK (row_version >= 1)
);

CREATE TABLE IF NOT EXISTS platform.landing_testimonial (
    testimonial_id uuid PRIMARY KEY,
    kind text NOT NULL,
    author_name text NOT NULL,
    author_role text NOT NULL,
    institution_name text NOT NULL,
    quote_text text NOT NULL,
    video_url text,
    video_media_id uuid REFERENCES platform.landing_media(media_id) ON DELETE RESTRICT,
    poster_media_id uuid REFERENCES platform.landing_media(media_id) ON DELETE RESTRICT,
    display_order integer NOT NULL DEFAULT 0,
    published boolean NOT NULL DEFAULT true,
    row_version integer NOT NULL DEFAULT 1,
    created_by uuid,
    updated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_landing_testimonial_kind CHECK (kind IN ('TEXT', 'VIDEO')),
    CONSTRAINT ck_landing_testimonial_order CHECK (display_order >= 0),
    CONSTRAINT ck_landing_testimonial_version CHECK (row_version >= 1)
);

CREATE TABLE IF NOT EXISTS platform.landing_enquiry (
    enquiry_id uuid PRIMARY KEY,
    enquiry_type text NOT NULL,
    institution_type text,
    student_range text,
    colleges_count integer NOT NULL DEFAULT 0,
    schools_count integer NOT NULL DEFAULT 0,
    students_estimate integer NOT NULL DEFAULT 0,
    staff_estimate integer NOT NULL DEFAULT 0,
    billing_cycle_months integer NOT NULL DEFAULT 12,
    include_setup_fee boolean NOT NULL DEFAULT true,
    selected_modules jsonb NOT NULL DEFAULT '[]'::jsonb,
    migration_support text,
    training_mode text,
    contact_name text NOT NULL,
    contact_email text NOT NULL,
    contact_phone text,
    institution_name text NOT NULL,
    notes text,
    quote_snapshot_json jsonb,
    status text NOT NULL DEFAULT 'NEW',
    internal_notes text,
    handled_by uuid,
    row_version integer NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_landing_enquiry_type CHECK (enquiry_type IN ('QUOTATION', 'DEMO')),
    CONSTRAINT ck_landing_enquiry_status CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'CLOSED')),
    CONSTRAINT ck_landing_enquiry_counts CHECK (
        colleges_count >= 0 AND schools_count >= 0 AND students_estimate >= 0 AND staff_estimate >= 0
    ),
    CONSTRAINT ck_landing_enquiry_cycle CHECK (billing_cycle_months BETWEEN 1 AND 36),
    CONSTRAINT ck_landing_enquiry_version CHECK (row_version >= 1)
);

CREATE INDEX IF NOT EXISTS ix_tenant_listed_directory
    ON platform.tenant(state, tenant_name) WHERE listed_in_directory = true;
CREATE INDEX IF NOT EXISTS ix_landing_testimonial_published_order
    ON platform.landing_testimonial(published, display_order);
CREATE INDEX IF NOT EXISTS ix_landing_enquiry_status_created
    ON platform.landing_enquiry(status, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_landing_config_hero_light ON platform.landing_page_config(hero_light_media_id) WHERE hero_light_media_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_landing_config_hero_dark ON platform.landing_page_config(hero_dark_media_id) WHERE hero_dark_media_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_landing_config_showcase_light ON platform.landing_page_config(showcase_light_media_id) WHERE showcase_light_media_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_landing_config_showcase_dark ON platform.landing_page_config(showcase_dark_media_id) WHERE showcase_dark_media_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_landing_testimonial_video_media ON platform.landing_testimonial(video_media_id) WHERE video_media_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_landing_testimonial_poster_media ON platform.landing_testimonial(poster_media_id) WHERE poster_media_id IS NOT NULL;

DROP TRIGGER IF EXISTS trg_touch ON platform.landing_media;
CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.landing_media
    FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON platform.landing_page_config;
CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.landing_page_config
    FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON platform.landing_testimonial;
CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.landing_testimonial
    FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON platform.landing_enquiry;
CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.landing_enquiry
    FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();

INSERT INTO platform.landing_page_config (
    config_id,
    hero_eyebrow,
    hero_title,
    hero_subtitle,
    hero_note,
    trust_label,
    why_choose_json,
    role_benefits_json,
    solutions_json,
    steps_json,
    contact_email,
    footer_tagline
)
VALUES (
    '00000000-0000-0000-0000-000000000101',
    'A connected education management platform',
    'Bring your entire institution into sync.',
    'From academic administration, governance, and fee management to parent communication and student workflows—EDU 360 connects the work that keeps education moving.',
    'Designed for Schools · Colleges · Universities',
    'One connected platform for every stakeholder in your institution',
    '[
      {"icon":"grid","title":"Academic management","description":"Organise campuses, departments, programmes, timetables, attendance, examinations and results in one system."},
      {"icon":"wallet","title":"Fees & finance","description":"Manage fee structures, concessions, receipts, instalments, payment tracking and collection reports."},
      {"icon":"bell","title":"Parent & family updates","description":"Keep families informed with notices, attendance alerts, fee reminders and real-time student progress."},
      {"icon":"users","title":"People & operations","description":"Coordinate staff records, leave workflows, delegations, approvals and everyday institutional processes."},
      {"icon":"chart","title":"Reports & insights","description":"Access operational reports with role-scoped visibility and PDF or Excel exports."},
      {"icon":"shield","title":"Governance & isolation","description":"Dedicated tenant realms, role-based access control, two-step login and immutable audit trails."}
    ]'::jsonb,
    '[
      {"roleKey":"leadership","label":"Institutional Leadership","headline":"Complete institutional oversight without chasing spreadsheets.","points":["Monitor admissions, attendance, fee collections and pending approvals across campuses.","Enforce term locks, role delegations and multi-level approval chains.","Review compliance-ready audit logs and executive analytics in real time."]},
      {"roleKey":"faculty","label":"Teachers & Faculty","headline":"Spend less time on paperwork and more time teaching.","points":["Mark daily or period attendance and manage class schedules cleanly.","Publish assignments, record marks and track student progress.","Submit leave requests and view department notices from any device."]},
      {"roleKey":"students","label":"Students","headline":"Every academic update, timetable and fee receipt in one portal.","points":["Check daily timetables, attendance records and upcoming exam schedules.","Submit assignments, view published results and track academic standing.","Access institutional circulars, self-service requests and active sessions."]},
      {"roleKey":"parents","label":"Parents & Guardians","headline":"Stay connected to your child''s progress and school updates.","points":["Receive timely attendance alerts, exam updates and official notices.","Review fee schedules, download receipts and track due dates easily.","Submit guardian requests and communicate with the institution securely."]}
    ]'::jsonb,
    '[
      {"title":"Schools","description":"Connect teachers, students, parents and school administration with attendance, fees, timetables and circulars."},
      {"title":"Colleges","description":"Coordinate programmes, semesters, faculty allocations, examinations, concessions and departmental approvals."},
      {"title":"Universities","description":"Support multi-institution hierarchies, central governance, delegated authority and consolidated reporting."}
    ]'::jsonb,
    '[
      {"stepNumber":"01","title":"Share your institution profile","description":"Tell us your institution type, student scale, priority modules and onboarding needs."},
      {"stepNumber":"02","title":"Explore a tailored walkthrough","description":"See the actual EDU 360 workflows mapped to your leadership, faculty, student and parent roles."},
      {"stepNumber":"03","title":"Receive your scoped proposal","description":"Get a structured implementation and commercial quotation aligned to EDU 360''s approved catalog."}
    ]'::jsonb,
    'hello@edu360.example',
    'One Platform. Every Institution. One Connected Ecosystem.'
)
ON CONFLICT (config_id) DO NOTHING;

GRANT SELECT, INSERT, UPDATE, DELETE ON platform.landing_media TO edu_platform;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform.landing_page_config TO edu_platform;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform.landing_testimonial TO edu_platform;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform.landing_enquiry TO edu_platform;
GRANT SELECT, INSERT, UPDATE ON platform.tenant TO edu_platform;

REVOKE ALL ON platform.landing_media FROM edu_app;
REVOKE ALL ON platform.landing_page_config FROM edu_app;
REVOKE ALL ON platform.landing_testimonial FROM edu_app;
REVOKE ALL ON platform.landing_enquiry FROM edu_app;

COMMENT ON COLUMN platform.tenant.listed_in_directory IS 'Opt-in flag allowing this active/trial tenant to appear in the public landing sign-in dropdown.';
COMMENT ON TABLE platform.landing_media IS 'Uploaded showcase images and video testimonials for the public landing page, malware-scanned via FileScanner.';
COMMENT ON TABLE platform.landing_page_config IS 'Platform-managed public marketing page configuration.';
COMMENT ON TABLE platform.landing_testimonial IS 'Published text and video testimonials displayed on the public landing page marquee.';
COMMENT ON TABLE platform.landing_enquiry IS 'Public quotation and demo requests submitted from the landing page with internal server-computed quote snapshot.';

-- The landing CMS is a platform-scoped capability, restricted to Product Owner and Platform Admin.
INSERT INTO ref.action_catalog VALUES (
    'LANDING_CMS_MANAGE', 290, 'Platform', 1,
    'Manage public landing page content, testimonials, media and inbound enquiries',
    false, NULL, 'V4.1'
) ON CONFLICT (action_code) DO NOTHING;
INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code) VALUES
    (9233, 'PRODUCT_OWNER', 'LANDING_CMS_MANAGE'),
    (9234, 'PLATFORM_ADMIN', 'LANDING_CMS_MANAGE')
ON CONFLICT (role_code, action_code) DO NOTHING;

COMMIT;
