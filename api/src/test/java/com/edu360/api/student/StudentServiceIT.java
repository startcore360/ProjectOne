package com.edu360.api.student;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class StudentServiceIT {
  @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
  @Test void cursorOrderingAndStudentStatusColumnsAreSupported() throws Exception {
    try(Connection c=java.sql.DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());Statement s=c.createStatement()){
      s.execute("CREATE TABLE student_profile(student_code text NOT NULL, student_profile_id uuid NOT NULL, status text NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED','WITHDRAWN','ALUMNI')), PRIMARY KEY(student_code,student_profile_id))");
      s.execute("INSERT INTO student_profile VALUES ('S-001','00000000-0000-0000-0000-000000000001','ACTIVE'),('S-002','00000000-0000-0000-0000-000000000002','SUSPENDED')");
      try(var rs=s.executeQuery("SELECT student_code,status FROM student_profile WHERE (student_code,student_profile_id)>('S-001','00000000-0000-0000-0000-000000000001') ORDER BY student_code LIMIT 1")){assertTrue(rs.next());assertEquals("S-002",rs.getString(1));assertEquals("SUSPENDED",rs.getString(2));}
      try(var rs=s.executeQuery("SELECT count(*) FROM student_profile")){assertTrue(rs.next());assertEquals(2,rs.getInt(1));}
    }
  }
}