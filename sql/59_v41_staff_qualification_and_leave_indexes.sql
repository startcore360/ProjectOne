BEGIN;
ALTER TABLE app.staff_document ADD COLUMN IF NOT EXISTS qualification_title text, ADD COLUMN IF NOT EXISTS awarding_institution text, ADD COLUMN IF NOT EXISTS qualification_level text, ADD COLUMN IF NOT EXISTS awarded_on date;
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.staff_document'::regclass AND conname='ck_staff_document_qualification_title') THEN ALTER TABLE app.staff_document ADD CONSTRAINT ck_staff_document_qualification_title CHECK (qualification_title IS NULL OR char_length(btrim(qualification_title)) BETWEEN 1 AND 150); END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.staff_document'::regclass AND conname='ck_staff_document_awarding_institution') THEN ALTER TABLE app.staff_document ADD CONSTRAINT ck_staff_document_awarding_institution CHECK (awarding_institution IS NULL OR char_length(btrim(awarding_institution)) BETWEEN 1 AND 150); END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.staff_document'::regclass AND conname='ck_staff_document_qualification_level') THEN ALTER TABLE app.staff_document ADD CONSTRAINT ck_staff_document_qualification_level CHECK (qualification_level IS NULL OR qualification_level IN ('CERTIFICATE','DIPLOMA','UNDERGRADUATE','POSTGRADUATE','DOCTORATE','OTHER')); END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.staff_document'::regclass AND conname='ck_staff_document_qualification_complete') THEN ALTER TABLE app.staff_document ADD CONSTRAINT ck_staff_document_qualification_complete CHECK ((qualification_title IS NULL AND awarding_institution IS NULL AND qualification_level IS NULL) OR (qualification_title IS NOT NULL AND awarding_institution IS NOT NULL AND qualification_level IS NOT NULL)); END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_staff_document_profile_type_created ON app.staff_document (tenant_id,staff_profile_id,document_type,created_at DESC);
CREATE INDEX IF NOT EXISTS ix_leave_request_staff_status_start ON app.leave_request (tenant_id,for_staff_profile_id,status,start_date) WHERE for_staff_profile_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_leave_balance_staff_year_type ON app.leave_balance (tenant_id,staff_profile_id,leave_year,leave_type_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_leave_balance_staff_type_year ON app.leave_balance (tenant_id,staff_profile_id,leave_type_id,leave_year);
CREATE UNIQUE INDEX IF NOT EXISTS uq_leave_type_institution_code ON app.leave_type (tenant_id,institution_id,audience,leave_code);
COMMENT ON COLUMN app.staff_document.qualification_title IS 'Qualification name for DEGREE evidence; stored on the existing scanned staff document record.';
COMMENT ON COLUMN app.staff_document.awarding_institution IS 'Awarding institution for a staff qualification.';
COMMENT ON COLUMN app.staff_document.qualification_level IS 'Qualification level: CERTIFICATE, DIPLOMA, UNDERGRADUATE, POSTGRADUATE, DOCTORATE or OTHER.';
COMMENT ON COLUMN app.staff_document.awarded_on IS 'Award date for qualification evidence, when supplied.';

-- M13 Staff Appraisals. The 20/50/30 weighting is a documented provisional default,
-- not an approved policy; peer feedback remains qualitative and does not change the rating.
CREATE TABLE IF NOT EXISTS app.appraisal_cycle (
 tenant_id uuid NOT NULL,
 id uuid NOT NULL,
 institution_id uuid NOT NULL,
 academic_year_id uuid NOT NULL,
 name text NOT NULL,
 start_date date NOT NULL,
 end_date date NOT NULL,
 status text NOT NULL DEFAULT 'DRAFT',
 created_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(),
 updated_by uuid NOT NULL,
 row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_appraisal_cycle PRIMARY KEY (tenant_id,id),
 CONSTRAINT uq_appraisal_cycle_identity UNIQUE (tenant_id,id,institution_id),
 CONSTRAINT fk_appraisal_cycle_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
 CONSTRAINT fk_appraisal_cycle_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT ck_appraisal_cycle_name CHECK (char_length(btrim(name)) BETWEEN 1 AND 160),
 CONSTRAINT ck_appraisal_cycle_dates CHECK (end_date>=start_date),
 CONSTRAINT ck_appraisal_cycle_status CHECK (status IN ('DRAFT','OPEN','CLOSED')),
 CONSTRAINT ck_appraisal_cycle_version CHECK (row_version>=1)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_appraisal_cycle_year_name ON app.appraisal_cycle(tenant_id,institution_id,academic_year_id,lower(name));
CREATE INDEX IF NOT EXISTS ix_appraisal_cycle_status_dates ON app.appraisal_cycle(tenant_id,institution_id,status,start_date,end_date);
CREATE TABLE IF NOT EXISTS app.appraisal_submission (
 tenant_id uuid NOT NULL,
 id uuid NOT NULL,
 cycle_id uuid NOT NULL,
 institution_id uuid NOT NULL,
 staff_id uuid NOT NULL,
 self_score numeric(5,2),
 hod_score numeric(5,2),
 principal_score numeric(5,2),
 final_rating numeric(5,2),
 status text NOT NULL DEFAULT 'DRAFT',
 comments_json jsonb NOT NULL DEFAULT '{}'::jsonb,
 created_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(),
 updated_by uuid NOT NULL,
 row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_appraisal_submission PRIMARY KEY (tenant_id,id),
 CONSTRAINT uq_appraisal_submission_staff UNIQUE (tenant_id,cycle_id,staff_id),
 CONSTRAINT uq_appraisal_submission_identity UNIQUE (tenant_id,id,cycle_id,institution_id),
 CONSTRAINT fk_appraisal_submission_cycle FOREIGN KEY (tenant_id,cycle_id,institution_id) REFERENCES app.appraisal_cycle(tenant_id,id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_appraisal_submission_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_appraisal_submission_staff FOREIGN KEY (tenant_id,staff_id) REFERENCES app.staff_profile(tenant_id,staff_profile_id) ON DELETE RESTRICT,
 CONSTRAINT ck_appraisal_submission_scores CHECK (
  (self_score IS NULL OR self_score BETWEEN 0 AND 100) AND
  (hod_score IS NULL OR hod_score BETWEEN 0 AND 100) AND
  (principal_score IS NULL OR principal_score BETWEEN 0 AND 100) AND
  (final_rating IS NULL OR final_rating BETWEEN 0 AND 100)),
 CONSTRAINT ck_appraisal_submission_status CHECK (status IN ('DRAFT','SELF_SUBMITTED','HOD_REVIEWED','PRINCIPAL_REVIEWED','COMPLETED')),
 CONSTRAINT ck_appraisal_submission_comments CHECK (jsonb_typeof(comments_json)='object'),
 CONSTRAINT ck_appraisal_submission_version CHECK (row_version>=1)
);
CREATE INDEX IF NOT EXISTS ix_appraisal_submission_cycle_status ON app.appraisal_submission(tenant_id,cycle_id,status);
CREATE INDEX IF NOT EXISTS ix_appraisal_submission_staff ON app.appraisal_submission(tenant_id,staff_id);
CREATE TABLE IF NOT EXISTS app.appraisal_review (
 tenant_id uuid NOT NULL,
 id uuid NOT NULL,
 cycle_id uuid NOT NULL,
 institution_id uuid NOT NULL,
 submission_id uuid NOT NULL,
 reviewer_staff_id uuid NOT NULL,
 review_type text NOT NULL DEFAULT 'PEER',
 feedback text NOT NULL,
 comments_json jsonb NOT NULL DEFAULT '{}'::jsonb,
 created_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(),
 updated_by uuid NOT NULL,
 row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_appraisal_review PRIMARY KEY (tenant_id,id),
 CONSTRAINT fk_appraisal_review_cycle FOREIGN KEY (tenant_id,cycle_id,institution_id) REFERENCES app.appraisal_cycle(tenant_id,id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_appraisal_review_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_appraisal_review_submission FOREIGN KEY (tenant_id,submission_id,cycle_id,institution_id) REFERENCES app.appraisal_submission(tenant_id,id,cycle_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_appraisal_review_reviewer FOREIGN KEY (tenant_id,reviewer_staff_id) REFERENCES app.staff_profile(tenant_id,staff_profile_id) ON DELETE RESTRICT,
 CONSTRAINT ck_appraisal_review_type CHECK (review_type IN ('PEER','HEAD_OF_DEPARTMENT','PRINCIPAL')),
 CONSTRAINT ck_appraisal_review_feedback CHECK (char_length(btrim(feedback)) BETWEEN 1 AND 5000),
 CONSTRAINT ck_appraisal_review_comments CHECK (jsonb_typeof(comments_json)='object'),
 CONSTRAINT ck_appraisal_review_version CHECK (row_version>=1)
);
CREATE INDEX IF NOT EXISTS ix_appraisal_review_submission ON app.appraisal_review(tenant_id,submission_id,review_type,created_at);
DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['appraisal_cycle','appraisal_submission','appraisal_review'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS trg_touch ON app.%I',t);
  EXECUTE format('CREATE TRIGGER trg_touch BEFORE UPDATE ON app.%I FOR EACH ROW EXECUTE FUNCTION app.fn_touch()',t);
  EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('DROP POLICY IF EXISTS p_tenant ON app.%I',t);
  EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid)',t);
  EXECUTE format('DROP POLICY IF EXISTS p_institution ON app.%I',t);
  EXECUTE format('CREATE POLICY p_institution ON app.%I AS RESTRICTIVE USING (institution_id IS NULL OR current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[])) WITH CHECK (institution_id IS NULL OR current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[]))',t);
 END LOOP;
END $$;
GRANT SELECT,INSERT,UPDATE,DELETE ON app.appraisal_cycle,app.appraisal_submission,app.appraisal_review TO edu_app;
GRANT SELECT ON app.appraisal_cycle,app.appraisal_submission,app.appraisal_review TO edu_report;
COMMENT ON TABLE app.appraisal_cycle IS 'Annual staff appraisal cycle (M13).';
COMMENT ON TABLE app.appraisal_submission IS 'Staff self-evaluation and HOD/Principal scores. Provisional final_rating = 20% self + 50% HOD + 30% Principal; policy decision pending.';
COMMENT ON TABLE app.appraisal_review IS 'Qualitative peer feedback and review comments. Peer feedback does not affect final_rating under the provisional scoring default.';
-- Appraisal action catalogue and baseline standard role grants.
INSERT INTO ref.action_catalog VALUES
 ('STAFF_APPRAISAL_CYCLE_MANAGE',283,'Staff records',1,'Create and manage annual appraisal cycles',false,NULL,'M13'),
 ('STAFF_APPRAISAL_VIEW',284,'Staff records',1,'View staff appraisal submissions and reviews',false,NULL,'M13'),
 ('STAFF_APPRAISAL_SELF_SUBMIT',285,'Staff records',1,'Submit own staff self-evaluation',false,NULL,'M13'),
 ('STAFF_APPRAISAL_PEER_REVIEW',286,'Staff records',1,'Submit qualitative peer appraisal feedback',false,NULL,'M13'),
 ('STAFF_APPRAISAL_HOD_SCORE',287,'Staff records',1,'Score staff appraisals as HOD',false,NULL,'M13'),
 ('STAFF_APPRAISAL_PRINCIPAL_REVIEW',288,'Staff records',1,'Complete principal appraisal review and final rating',false,NULL,'M13')
ON CONFLICT DO NOTHING;
DO $$
DECLARE next_id integer;
BEGIN
 SELECT COALESCE(MAX(standard_role_action_id),0)+1 INTO next_id FROM ref.standard_role_action;
 INSERT INTO ref.standard_role_action(standard_role_action_id,role_code,action_code,limited_to)
 SELECT next_id+v.n,v.role_code,v.action_code,v.limited_to
 FROM (VALUES
  (0,'HR_MANAGER','STAFF_APPRAISAL_CYCLE_MANAGE','Own institution'),
  (1,'HR_EXECUTIVE','STAFF_APPRAISAL_CYCLE_MANAGE','Own institution'),
  (2,'UNIVERSITY_HR_MANAGER','STAFF_APPRAISAL_CYCLE_MANAGE','Own scope'),
  (3,'HR_MANAGER','STAFF_APPRAISAL_VIEW','Own institution'),
  (4,'HR_EXECUTIVE','STAFF_APPRAISAL_VIEW','Own institution'),
  (5,'UNIVERSITY_HR_MANAGER','STAFF_APPRAISAL_VIEW','Own scope'),
  (6,'HEAD_OF_DEPARTMENT','STAFF_APPRAISAL_VIEW','Own department'),
  (20,'CLASS_TEACHER','STAFF_APPRAISAL_SELF_SUBMIT','Own staff profile'),
  (22,'CLASS_TEACHER','STAFF_APPRAISAL_PEER_REVIEW','Own institution'),
  (21,'SUBJECT_TEACHER','STAFF_APPRAISAL_SELF_SUBMIT','Own staff profile'),
  (23,'SUBJECT_TEACHER','STAFF_APPRAISAL_PEER_REVIEW','Own institution'),
  (7,'PRINCIPAL_COLLEGE','STAFF_APPRAISAL_VIEW','Own institution'),
  (8,'SCHOOL_PRINCIPAL','STAFF_APPRAISAL_VIEW','Own institution'),
  (9,'HR_MANAGER','STAFF_APPRAISAL_HOD_SCORE','Own institution'),
  (10,'HEAD_OF_DEPARTMENT','STAFF_APPRAISAL_HOD_SCORE','Own department'),
  (11,'PRINCIPAL_COLLEGE','STAFF_APPRAISAL_PRINCIPAL_REVIEW','Own institution'),
  (12,'SCHOOL_PRINCIPAL','STAFF_APPRAISAL_PRINCIPAL_REVIEW','Own institution'),
  (13,'HR_MANAGER','STAFF_APPRAISAL_PRINCIPAL_REVIEW','Own institution'),
  (14,'HR_EXECUTIVE','STAFF_APPRAISAL_PRINCIPAL_REVIEW','Own institution'),
  (15,'UNIVERSITY_HR_MANAGER','STAFF_APPRAISAL_PRINCIPAL_REVIEW','Own scope'),
  (16,'HR_MANAGER','STAFF_APPRAISAL_PEER_REVIEW','Own institution'),
  (17,'HEAD_OF_DEPARTMENT','STAFF_APPRAISAL_PEER_REVIEW','Own department'),
  (18,'PRINCIPAL_COLLEGE','STAFF_APPRAISAL_PEER_REVIEW','Own institution'),
  (19,'SCHOOL_PRINCIPAL','STAFF_APPRAISAL_PEER_REVIEW','Own institution')
 ) AS v(n,role_code,action_code,limited_to)
 WHERE EXISTS (SELECT 1 FROM ref.standard_role_action a WHERE a.role_code=v.role_code)
 ON CONFLICT DO NOTHING;
END $$;

COMMIT;
