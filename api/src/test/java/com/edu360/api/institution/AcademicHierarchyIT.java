package com.edu360.api.institution;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
@SpringBootTest @ActiveProfiles("test")
class AcademicHierarchyIT {
 @Autowired JdbcTemplate jdbc;
 private boolean col(String table,String column){Integer n=jdbc.queryForObject("select count(*) from information_schema.columns where table_schema='app' and table_name=? and column_name=?",Integer.class,table,column);return n!=null&&n>0;}
 @Test void migration57ColumnsAndExclusionExist(){assertThat(col("institution","settings_json")).isTrue();assertThat(col("academic_session","is_current")).isTrue();Integer n=jdbc.queryForObject("select count(*) from pg_constraint where conname='ex_academic_session_nonoverlap'",Integer.class);assertThat(n).isPositive();}
 @Test void migration58ColumnsAndCurriculumVersionExist(){assertThat(col("program","degree_level")).isTrue();assertThat(col("semester","is_locked")).isTrue();assertThat(col("semester","start_date")).isTrue();assertThat(col("semester","end_date")).isTrue();assertThat(col("section","capacity")).isTrue();Integer n=jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='app' and table_name='program_curriculum_version'",Integer.class);assertThat(n).isPositive();}
}