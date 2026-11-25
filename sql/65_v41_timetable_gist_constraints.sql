BEGIN;
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS app.room (
 tenant_id uuid NOT NULL, room_id uuid NOT NULL, institution_id uuid NOT NULL,
 room_number text NOT NULL, building text, capacity integer NOT NULL,
 room_type text NOT NULL, status text NOT NULL DEFAULT 'ACTIVE',
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL,
 row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_room PRIMARY KEY (tenant_id,room_id),
 CONSTRAINT uq_room_identity UNIQUE (tenant_id,institution_id,room_id),
 CONSTRAINT uq_room_number UNIQUE (tenant_id,institution_id,room_number),
 CONSTRAINT fk_room_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
 CONSTRAINT fk_room_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT ck_room_number CHECK (char_length(btrim(room_number)) BETWEEN 1 AND 40),
 CONSTRAINT ck_room_capacity CHECK (capacity BETWEEN 1 AND 10000),
 CONSTRAINT ck_room_type CHECK (room_type IN ('LECTURE_HALL','LAB','AUDITORIUM')),
 CONSTRAINT ck_room_status CHECK (status IN ('ACTIVE','INACTIVE')),
 CONSTRAINT ck_room_version CHECK (row_version>=1),
 CONSTRAINT ck_room_updated CHECK (updated_at>=created_at)
);

CREATE TABLE IF NOT EXISTS app.timetable_slot (
 tenant_id uuid NOT NULL, timetable_slot_id uuid NOT NULL, institution_id uuid NOT NULL,
 academic_session_id uuid NOT NULL, section_id uuid NOT NULL, course_offering_id uuid NOT NULL,
 teacher_id uuid NOT NULL, room_id uuid, day_of_week smallint NOT NULL,
 start_time time NOT NULL, end_time time NOT NULL, online_meeting_url text,
 status text NOT NULL DEFAULT 'ACTIVE',
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL,
 row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_timetable_slot PRIMARY KEY (tenant_id,timetable_slot_id),
 CONSTRAINT uq_timetable_slot_identity UNIQUE (tenant_id,institution_id,timetable_slot_id),
 CONSTRAINT fk_timetable_slot_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
 CONSTRAINT fk_timetable_slot_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_timetable_slot_session FOREIGN KEY (tenant_id,academic_session_id) REFERENCES app.academic_session(tenant_id,academic_session_id) ON DELETE RESTRICT,
 CONSTRAINT fk_timetable_slot_section FOREIGN KEY (tenant_id,institution_id,section_id) REFERENCES app.section(tenant_id,institution_id,section_id) ON DELETE RESTRICT,
 CONSTRAINT fk_timetable_slot_offering FOREIGN KEY (tenant_id,institution_id,course_offering_id) REFERENCES app.course_offering(tenant_id,institution_id,course_offering_id) ON DELETE RESTRICT,
 CONSTRAINT fk_timetable_slot_teacher FOREIGN KEY (tenant_id,teacher_id) REFERENCES app.staff_profile(tenant_id,staff_profile_id) ON DELETE RESTRICT,
 CONSTRAINT fk_timetable_slot_room FOREIGN KEY (tenant_id,institution_id,room_id) REFERENCES app.room(tenant_id,institution_id,room_id) ON DELETE RESTRICT,
 CONSTRAINT ck_timetable_slot_day CHECK (day_of_week BETWEEN 1 AND 7),
 CONSTRAINT ck_timetable_slot_time CHECK (end_time>start_time),
 CONSTRAINT ck_timetable_slot_status CHECK (status IN ('ACTIVE','CANCELLED')),
 CONSTRAINT ck_timetable_slot_meeting CHECK (online_meeting_url IS NULL OR online_meeting_url ~ '^https://'),
 CONSTRAINT ck_timetable_slot_room_or_online CHECK (room_id IS NOT NULL OR online_meeting_url IS NOT NULL),
 CONSTRAINT ck_timetable_slot_version CHECK (row_version>=1),
 CONSTRAINT ck_timetable_slot_updated CHECK (updated_at>=created_at)
);

CREATE OR REPLACE FUNCTION app.fn_timetable_slot_scope_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE v_session_start date; v_session_end date; v_teacher_status text; v_room_status text;
BEGIN
 SELECT a.start_date,a.end_date INTO v_session_start,v_session_end
  FROM app.academic_session a
  JOIN app.section s ON s.tenant_id=a.tenant_id AND s.academic_session_id=a.academic_session_id
   AND s.institution_id=NEW.institution_id AND s.section_id=NEW.section_id AND s.status='ACTIVE'
  JOIN app.course_offering o ON o.tenant_id=a.tenant_id AND o.academic_session_id=a.academic_session_id
   AND o.institution_id=NEW.institution_id AND o.course_offering_id=NEW.course_offering_id
  WHERE a.tenant_id=NEW.tenant_id AND a.academic_session_id=NEW.academic_session_id
   AND (a.institution_id IS NULL OR a.institution_id=NEW.institution_id)
   AND o.semester_id IS NOT DISTINCT FROM s.semester_id AND o.class_id IS NOT DISTINCT FROM s.class_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'TIMETABLE_SECTION_COURSE_SESSION_MISMATCH' USING ERRCODE='23514'; END IF;
 SELECT employment_status INTO v_teacher_status FROM app.staff_profile
  WHERE tenant_id=NEW.tenant_id AND staff_profile_id=NEW.teacher_id;
 IF v_teacher_status IS DISTINCT FROM 'ACTIVE' OR NOT EXISTS (
  SELECT 1 FROM app.staff_assignment x
   WHERE x.tenant_id=NEW.tenant_id AND x.staff_profile_id=NEW.teacher_id
    AND x.institution_id=NEW.institution_id AND x.status='ACTIVE'
    AND x.effective_from<=v_session_end AND (x.effective_to IS NULL OR x.effective_to>=v_session_start)
 ) THEN RAISE EXCEPTION 'TIMETABLE_TEACHER_NOT_ASSIGNED' USING ERRCODE='23514'; END IF;
 IF NEW.room_id IS NOT NULL THEN
  SELECT status INTO v_room_status FROM app.room
   WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id AND room_id=NEW.room_id;
  IF v_room_status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'TIMETABLE_ROOM_INACTIVE' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_timetable_slot_scope_guard ON app.timetable_slot;
CREATE TRIGGER trg_timetable_slot_scope_guard BEFORE INSERT OR UPDATE ON app.timetable_slot
 FOR EACH ROW EXECUTE FUNCTION app.fn_timetable_slot_scope_guard();

DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='ex_timetable_teacher_no_overlap') THEN
  ALTER TABLE app.timetable_slot ADD CONSTRAINT ex_timetable_teacher_no_overlap
   EXCLUDE USING gist (tenant_id WITH =,teacher_id WITH =,day_of_week WITH =,
    tsrange(date '2000-01-03'+start_time,date '2000-01-03'+end_time,'[)') WITH &&)
   WHERE (status='ACTIVE');
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='ex_timetable_room_no_overlap') THEN
  ALTER TABLE app.timetable_slot ADD CONSTRAINT ex_timetable_room_no_overlap
   EXCLUDE USING gist (tenant_id WITH =,institution_id WITH =,room_id WITH =,day_of_week WITH =,
    tsrange(date '2000-01-03'+start_time,date '2000-01-03'+end_time,'[)') WITH &&)
   WHERE (status='ACTIVE' AND room_id IS NOT NULL);
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS app.online_session (
 tenant_id uuid NOT NULL, online_session_id uuid NOT NULL, institution_id uuid NOT NULL,
 timetable_slot_id uuid NOT NULL, provider text NOT NULL, meeting_url text NOT NULL,
 access_token_hash char(64) NOT NULL, token_expires_at timestamptz NOT NULL,
 status text NOT NULL DEFAULT 'ACTIVE',
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL,
 row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_online_session PRIMARY KEY (tenant_id,online_session_id),
 CONSTRAINT uq_online_session_identity UNIQUE (tenant_id,institution_id,online_session_id),
 CONSTRAINT uq_online_session_token UNIQUE (tenant_id,access_token_hash),
 CONSTRAINT fk_online_session_slot FOREIGN KEY (tenant_id,institution_id,timetable_slot_id)
  REFERENCES app.timetable_slot(tenant_id,institution_id,timetable_slot_id) ON DELETE RESTRICT,
 CONSTRAINT ck_online_session_provider CHECK (provider IN ('ZOOM','GOOGLE_MEET','TEAMS','CUSTOM')),
 CONSTRAINT ck_online_session_url CHECK (meeting_url ~ '^https://'),
 CONSTRAINT ck_online_session_token_hash CHECK (access_token_hash ~ '^[0-9a-f]{64}$'),
 CONSTRAINT ck_online_session_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
 CONSTRAINT ck_online_session_version CHECK (row_version>=1),
 CONSTRAINT ck_online_session_updated CHECK (updated_at>=created_at)
);
CREATE TABLE IF NOT EXISTS app.online_session_access_log (
 tenant_id uuid NOT NULL, access_log_id uuid NOT NULL, institution_id uuid NOT NULL,
 online_session_id uuid NOT NULL, student_profile_id uuid NOT NULL,
 access_result text NOT NULL, accessed_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT pk_online_session_access_log PRIMARY KEY (tenant_id,access_log_id),
 CONSTRAINT fk_online_access_session FOREIGN KEY (tenant_id,institution_id,online_session_id)
  REFERENCES app.online_session(tenant_id,institution_id,online_session_id) ON DELETE RESTRICT,
 CONSTRAINT fk_online_access_student FOREIGN KEY (tenant_id,institution_id,student_profile_id)
  REFERENCES app.student_profile(tenant_id,institution_id,student_profile_id) ON DELETE RESTRICT,
 CONSTRAINT ck_online_access_result CHECK (access_result IN ('ALLOWED','DENIED'))
);
CREATE INDEX IF NOT EXISTS ix_timetable_section_day ON app.timetable_slot(tenant_id,institution_id,section_id,day_of_week,start_time);
CREATE INDEX IF NOT EXISTS ix_timetable_teacher_day ON app.timetable_slot(tenant_id,teacher_id,day_of_week) WHERE status='ACTIVE';
CREATE INDEX IF NOT EXISTS ix_online_access_student_time ON app.online_session_access_log(tenant_id,student_profile_id,accessed_at DESC);

DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['room','timetable_slot','online_session','online_session_access_log'] LOOP
  EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('DROP POLICY IF EXISTS p_tenant ON app.%I',t);
  EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid)',t);
  EXECUTE format('DROP POLICY IF EXISTS p_institution ON app.%I',t);
  EXECUTE format('CREATE POLICY p_institution ON app.%I AS RESTRICTIVE USING (current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[])) WITH CHECK (current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[]))',t);
 END LOOP;
END $$;
DROP TRIGGER IF EXISTS trg_touch ON app.room;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.room FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON app.timetable_slot;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.timetable_slot FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON app.online_session;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.online_session FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
DROP TRIGGER IF EXISTS trg_online_session_access_append_only ON app.online_session_access_log;
CREATE TRIGGER trg_online_session_access_append_only BEFORE UPDATE OR DELETE ON app.online_session_access_log
 FOR EACH ROW EXECUTE FUNCTION app.fn_append_only();
GRANT SELECT,INSERT,UPDATE,DELETE ON app.room,app.timetable_slot,app.online_session TO edu_app;
GRANT SELECT ON app.room,app.timetable_slot,app.online_session,app.online_session_access_log TO edu_report;
GRANT SELECT,INSERT ON app.online_session_access_log TO edu_app;
COMMENT ON TABLE app.timetable_slot IS 'Weekly recurring timetable allocation; GiST exclusions prevent teacher and room overlaps.';
COMMENT ON COLUMN app.online_session.access_token_hash IS 'SHA-256 hash only; plaintext access tokens are never persisted.';
COMMIT;