package com.edu360.api.student;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class StudentNoteGuardIT {
  @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
  @Test void noteCannotBeUpdatedAndActiveStudentHoldBlocksRetentionDelete() throws Exception {
    try(Connection c=java.sql.DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());Statement s=c.createStatement()){
      s.execute("CREATE SCHEMA app; CREATE ROLE edu_retention; CREATE TABLE app.legal_hold(tenant_id uuid,subject_kind text,subject_id uuid,released_at timestamptz); CREATE TABLE app.student_note(note_id int,tenant_id uuid,student_profile_id uuid,delete_after date,body text)");
      s.execute("CREATE FUNCTION app.fn_note_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_OP='UPDATE' THEN RAISE EXCEPTION 'NOTE_IMMUTABLE: notes are corrected by a new linked note' USING ERRCODE='42501'; END IF; IF current_user <> 'edu_retention' THEN RAISE EXCEPTION 'NOTE_IMMUTABLE: only the retention job may delete' USING ERRCODE='42501'; END IF; IF OLD.delete_after IS NULL OR OLD.delete_after>current_date THEN RAISE EXCEPTION 'NOTE_RETENTION' USING ERRCODE='23514'; END IF; IF EXISTS(SELECT 1 FROM app.legal_hold h WHERE h.tenant_id=OLD.tenant_id AND h.subject_kind='STUDENT' AND h.subject_id=OLD.student_profile_id AND h.released_at IS NULL) THEN RAISE EXCEPTION 'LEGAL_HOLD: deletion blocked' USING ERRCODE='23514'; END IF; RETURN OLD; END $$");
      s.execute("GRANT USAGE ON SCHEMA app TO edu_retention; GRANT SELECT, DELETE ON app.student_note TO edu_retention; GRANT SELECT ON app.legal_hold TO edu_retention");
      s.execute("CREATE TRIGGER note_guard BEFORE UPDATE OR DELETE ON app.student_note FOR EACH ROW EXECUTE FUNCTION app.fn_note_guard()");
      s.execute("INSERT INTO app.student_note VALUES(1,'00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',current_date-1,'note'); INSERT INTO app.legal_hold VALUES('00000000-0000-0000-0000-000000000001','STUDENT','00000000-0000-0000-0000-000000000002',NULL)");
      var update=s.executeQuery("SELECT 1"); update.close();
      var updateError=assertThrows(java.sql.SQLException.class,()->s.execute("UPDATE app.student_note SET body='changed' WHERE note_id=1"));
      assertTrue(updateError.getMessage().contains("NOTE_IMMUTABLE"));
      s.execute("SET ROLE edu_retention");
      var deleteError=assertThrows(java.sql.SQLException.class,()->s.execute("DELETE FROM app.student_note WHERE note_id=1"));
      assertTrue(deleteError.getMessage().contains("LEGAL_HOLD"));
    }
  }
}