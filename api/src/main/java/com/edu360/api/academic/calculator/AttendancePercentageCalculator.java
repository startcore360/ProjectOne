package com.edu360.api.academic.calculator;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AttendancePercentageCalculator {
    private final JdbcTemplate jdbc;
    public AttendancePercentageCalculator(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional(readOnly=true)
    public Map<String,Object> calculate(UUID tenantId,UUID institutionId,UUID enrollmentId){
        return jdbc.queryForMap("""
            SELECT enrollment_id,total_valid_sessions,sessions_attended,exempted_medical_sessions,
                   standard_percentage,adjusted_percentage
              FROM app.v_attendance_percentage
             WHERE tenant_id=? AND institution_id=? AND enrollment_id=?
            """,tenantId,institutionId,enrollmentId);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> eligibility(UUID tenantId,UUID institutionId,UUID enrollmentId){
        return jdbc.queryForMap("""
            SELECT enrollment_id,attendance_percentage,threshold_percentage,eligible,threshold_scope
              FROM app.fn_attendance_exam_eligibility(?,?,?)
            """,tenantId,institutionId,enrollmentId);
    }
}