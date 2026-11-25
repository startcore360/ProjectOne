package com.edu360.api.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TimetableGiSTConflictIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void timetableMigrationInstallsBothExclusionConstraints() {
        Integer teacher = jdbc.queryForObject("""
            SELECT count(*) FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass
             AND conname='ex_timetable_teacher_no_overlap' AND contype='x'
            """, Integer.class);
        Integer room = jdbc.queryForObject("""
            SELECT count(*) FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass
             AND conname='ex_timetable_room_no_overlap' AND contype='x'
            """, Integer.class);
        assertThat(teacher).isEqualTo(1);
        assertThat(room).isEqualTo(1);
    }

    @Test
    void postgresGiSTExclusionRejectsOverlappingTeacherBookingsWithSqlState23P01() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE t6_teacher_overlap_probe (tenant_id uuid, teacher_id uuid, day_of_week smallint, period tsrange)");
                statement.execute("ALTER TABLE t6_teacher_overlap_probe ADD CONSTRAINT t6_teacher_overlap_probe_excl EXCLUDE USING gist (tenant_id WITH =, teacher_id WITH =, day_of_week WITH =, period WITH &&)");
            }
            UUID tenant = UUID.randomUUID();
            UUID teacher = UUID.randomUUID();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO t6_teacher_overlap_probe VALUES (?,?,?,tsrange(?::timestamp,?::timestamp,'[)'))")) {
                insert.setObject(1, tenant); insert.setObject(2, teacher); insert.setInt(3, 1);
                insert.setString(4, "2026-11-02 09:00"); insert.setString(5, "2026-11-02 10:00");
                insert.executeUpdate();
                insert.setString(4, "2026-11-02 09:30"); insert.setString(5, "2026-11-02 10:30");
                SQLException conflict = assertThrows(SQLException.class, insert::executeUpdate);
                assertThat(conflict.getSQLState()).isEqualTo("23P01");
            }
            return null;
        });
    }
}