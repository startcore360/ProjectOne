BEGIN;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE IF NOT EXISTS app.room (
 tenant_id uuid NOT NULL, room_id uuid NOT NULL, institution_id uuid NOT NULL,
 room_number text NOT NULL, building text, capacity integer NOT NULL, room_type text NOT NULL,
 status text NOT NULL DEFAULT 'ACTIVE', created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL, row_version integer NOT NULL DEFAULT 1,
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
ALTER TABLE app.timetable_slot ADD COLUMN IF NOT EXISTS teacher_id uuid;
ALTER TABLE app.timetable_slot ADD COLUMN IF NOT EXISTS room_id uuid;
UPDATE app.timetable_slot ts SET teacher_id=sp.staff_profile_id
 FROM app.subject_allocation sa
 JOIN app.staff_assignment st ON st.tenant_id=sa.tenant_id AND st.staff_assignment_id=sa.staff_assignment_id
 JOIN app.staff_profile sp ON sp.tenant_id=st.tenant_id AND sp.staff_profile_id=st.staff_profile_id
 WHERE ts.tenant_id=sa.tenant_id AND ts.subject_allocation_id=sa.subject_allocation_id AND ts.teacher_id IS NULL;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='fk_timetable_slot_teacher_profile') THEN
  ALTER TABLE app.timetable_slot ADD CONSTRAINT fk_timetable_slot_teacher_profile
   FOREIGN KEY (tenant_id,teacher_id) REFERENCES app.staff_profile(tenant_id,staff_profile_id) ON DELETE RESTRICT;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='fk_timetable_slot_room') THEN
  ALTER TABLE app.timetable_slot ADD CONSTRAINT fk_timetable_slot_room
   FOREIGN KEY (tenant_id,institution_id,room_id) REFERENCES app.room(tenant_id,institution_id,room_id) ON DELETE RESTRICT;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='ex_timetable_teacher_no_overlap') THEN
  ALTER TABLE app.timetable_slot ADD CONSTRAINT ex_timetable_teacher_no_overlap
   EXCLUDE USING gist (tenant_id WITH =,teacher_id WITH =,day_of_week WITH =,
    daterange(valid_from,valid_to,'[]') WITH &&,
    tsrange(date '2000-01-03'+start_time,date '2000-01-03'+end_time,'[)') WITH &&)
   WHERE (status IN ('DRAFT','PUBLISHED') AND teacher_id IS NOT NULL);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='ex_timetable_room_no_overlap') THEN
  ALTER TABLE app.timetable_slot ADD CONSTRAINT ex_timetable_room_no_overlap
   EXCLUDE USING gist (tenant_id WITH =,institution_id WITH =,room_id WITH =,day_of_week WITH =,
    daterange(valid_from,valid_to,'[]') WITH &&,
    tsrange(date '2000-01-03'+start_time,date '2000-01-03'+end_time,'[)') WITH &&)
   WHERE (status IN ('DRAFT','PUBLISHED') AND room_id IS NOT NULL);
 END IF;
END $$;
CREATE OR REPLACE FUNCTION app.fn_timetable_slot_scope_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE v_section uuid; v_group uuid; v_course_session uuid; v_staff_profile uuid; v_staff_status text;
 v_staff_institution uuid; v_staff_from date; v_staff_to date; v_employment_status text;
 v_session_start date; v_session_end date; v_room_status text;
BEGIN
 IF TG_OP='UPDATE' AND NEW.status='SUPERSEDED' THEN RETURN NEW; END IF;
 SELECT sa.section_id,sa.subject_group_id,co.academic_session_id,st.staff_profile_id,st.status,
        st.institution_id,st.effective_from,st.effective_to,sp.employment_status,ac.start_date,ac.end_date
 INTO v_section,v_group,v_course_session,v_staff_profile,v_staff_status,
      v_staff_institution,v_staff_from,v_staff_to,v_employment_status,v_session_start,v_session_end
 FROM app.subject_allocation sa
 JOIN app.course_offering co ON co.tenant_id=sa.tenant_id AND co.institution_id=sa.institution_id AND co.course_offering_id=sa.course_offering_id
 JOIN app.academic_session ac ON ac.tenant_id=co.tenant_id AND ac.academic_session_id=co.academic_session_id
 JOIN app.staff_assignment st ON st.tenant_id=sa.tenant_id AND st.staff_assignment_id=sa.staff_assignment_id
 JOIN app.staff_profile sp ON sp.tenant_id=st.tenant_id AND sp.staff_profile_id=st.staff_profile_id
 WHERE sa.tenant_id=NEW.tenant_id AND sa.institution_id=NEW.institution_id
  AND sa.subject_allocation_id=NEW.subject_allocation_id AND sa.status='ACTIVE';
 IF NOT FOUND THEN RAISE EXCEPTION 'TIMETABLE_SUBJECT_ALLOCATION_INVALID' USING ERRCODE='23514'; END IF;
 IF v_section IS DISTINCT FROM NEW.section_id OR v_group IS DISTINCT FROM NEW.subject_group_id
  OR v_course_session IS DISTINCT FROM NEW.academic_session_id THEN
  RAISE EXCEPTION 'TIMETABLE_ALLOCATION_SCOPE_MISMATCH' USING ERRCODE='23514';
 END IF;
 IF NEW.valid_from<v_session_start OR NEW.valid_from>v_session_end
  OR (NEW.valid_to IS NOT NULL AND NEW.valid_to>v_session_end) THEN
  RAISE EXCEPTION 'TIMETABLE_VALIDITY_OUTSIDE_SESSION' USING ERRCODE='23514';
 END IF;
 IF v_staff_status IS DISTINCT FROM 'ACTIVE' OR v_employment_status IS DISTINCT FROM 'ACTIVE'
  OR (v_staff_institution IS NOT NULL AND v_staff_institution<>NEW.institution_id)
  OR v_staff_from>COALESCE(NEW.valid_to,v_session_end)
  OR (v_staff_to IS NOT NULL AND v_staff_to<NEW.valid_from) THEN
  RAISE EXCEPTION 'TIMETABLE_TEACHER_NOT_ASSIGNED' USING ERRCODE='23514';
 END IF;
 IF NEW.section_id IS NOT NULL THEN
  IF NOT EXISTS(SELECT 1 FROM app.section s WHERE s.tenant_id=NEW.tenant_id AND s.institution_id=NEW.institution_id
   AND s.section_id=NEW.section_id AND s.academic_session_id=NEW.academic_session_id AND s.status='ACTIVE') THEN
   RAISE EXCEPTION 'TIMETABLE_SECTION_INVALID' USING ERRCODE='23514';
  END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM app.subject_group g WHERE g.tenant_id=NEW.tenant_id AND g.institution_id=NEW.institution_id
   AND g.subject_group_id=NEW.subject_group_id AND g.status='ACTIVE') THEN
   RAISE EXCEPTION 'TIMETABLE_SUBJECT_GROUP_INVALID' USING ERRCODE='23514';
  END IF;
 END IF;
 IF NEW.room_id IS NOT NULL THEN
  SELECT status INTO v_room_status FROM app.room WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id AND room_id=NEW.room_id;
  IF v_room_status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'TIMETABLE_ROOM_INACTIVE' USING ERRCODE='23514'; END IF;
 END IF;
 NEW.teacher_id:=v_staff_profile;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_timetable_slot_scope_guard ON app.timetable_slot;
CREATE TRIGGER trg_timetable_slot_scope_guard BEFORE INSERT OR UPDATE ON app.timetable_slot
 FOR EACH ROW EXECUTE FUNCTION app.fn_timetable_slot_scope_guard();

ALTER TABLE app.meeting_link ADD COLUMN IF NOT EXISTS access_token_hash char(64);
ALTER TABLE app.meeting_link ADD COLUMN IF NOT EXISTS token_expires_at timestamptz;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.meeting_link'::regclass AND conname='ck_meeting_link_access_token_hash') THEN
  ALTER TABLE app.meeting_link ADD CONSTRAINT ck_meeting_link_access_token_hash
   CHECK (access_token_hash IS NULL OR access_token_hash ~ '^[0-9a-f]{64}$');
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.meeting_link'::regclass AND conname='ck_meeting_link_token_expiry') THEN
  ALTER TABLE app.meeting_link ADD CONSTRAINT ck_meeting_link_token_expiry
   CHECK ((access_token_hash IS NULL AND token_expires_at IS NULL) OR (access_token_hash IS NOT NULL AND token_expires_at IS NOT NULL));
 END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_link_access_token ON app.meeting_link(tenant_id,access_token_hash)
 WHERE access_token_hash IS NOT NULL;
CREATE TABLE IF NOT EXISTS app.online_session_access_log (
 tenant_id uuid NOT NULL, access_log_id uuid NOT NULL, institution_id uuid NOT NULL,
 meeting_link_id uuid NOT NULL, student_profile_id uuid NOT NULL, access_result text NOT NULL,
 accessed_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT pk_online_session_access_log PRIMARY KEY (tenant_id,access_log_id),
 CONSTRAINT fk_online_access_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
 CONSTRAINT fk_online_access_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_online_access_meeting_link FOREIGN KEY (tenant_id,institution_id,meeting_link_id)
  REFERENCES app.meeting_link(tenant_id,institution_id,meeting_link_id) ON DELETE RESTRICT,
 CONSTRAINT fk_online_access_student FOREIGN KEY (tenant_id,institution_id,student_profile_id)
  REFERENCES app.student_profile(tenant_id,institution_id,student_profile_id) ON DELETE RESTRICT,
 CONSTRAINT ck_online_access_result CHECK (access_result IN ('ALLOWED','DENIED'))
);
CREATE INDEX IF NOT EXISTS ix_online_access_student_time ON app.online_session_access_log(tenant_id,student_profile_id,accessed_at DESC);
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['room','online_session_access_log'] LOOP
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
DROP TRIGGER IF EXISTS trg_online_session_access_append_only ON app.online_session_access_log;
CREATE TRIGGER trg_online_session_access_append_only BEFORE UPDATE OR DELETE ON app.online_session_access_log
 FOR EACH ROW EXECUTE FUNCTION app.fn_append_only();
GRANT SELECT,INSERT,UPDATE,DELETE ON app.room TO edu_app;
GRANT SELECT ON app.room,app.online_session_access_log TO edu_report;
GRANT SELECT,INSERT ON app.online_session_access_log TO edu_app;
COMMENT ON TABLE app.room IS 'Institution-owned physical timetable resource.';
COMMENT ON COLUMN app.timetable_slot.teacher_id IS 'Derived from the active subject allocation staff assignment; indexed by GiST for clash prevention.';
COMMENT ON COLUMN app.timetable_slot.room_id IS 'Room entity; room_label remains for legacy schedules.';
COMMENT ON COLUMN app.meeting_link.access_token_hash IS 'SHA-256 hash of the random student access token; plaintext is never persisted.';
COMMENT ON TABLE app.online_session_access_log IS 'Append-only audit of student online meeting access attempts.';
COMMIT;