package com.edu360.api.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class NoDuesClearanceIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void feeLibraryAndDepartmentEquipmentIntegrationsArePresent() {
        for (String relation : new String[]{"app.v_fee_invoice", "app.book_circulation",
                "app.library_fine", "app.v_student_library_dues_flag",
                "app.department_equipment_clearance"}) {
            Boolean exists = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, relation);
            assertThat(exists).as(relation + " is required for No-Dues").isTrue();
        }
    }

    @Test
    void libraryDuesReadModelUsesCirculationAndFineLedgerUnderInvokerSecurity() {
        String definition = jdbc.queryForObject(
                "SELECT pg_get_viewdef('app.v_student_library_dues_flag'::regclass,true)", String.class);
        assertThat(definition).contains("book_circulation", "library_fine", "returned_at", "outstanding");
        Boolean securityInvoker = jdbc.queryForObject("""
            SELECT COALESCE('security_invoker=true'=ANY(reloptions),false)
              FROM pg_class WHERE oid='app.v_student_library_dues_flag'::regclass
            """, Boolean.class);
        assertThat(securityInvoker).isTrue();
    }

    @Test
    void departmentEquipmentClearanceIsForcedRlsAndInstitutionScoped() {
        Boolean forced = jdbc.queryForObject("""
            SELECT relrowsecurity AND relforcerowsecurity
              FROM pg_class WHERE oid='app.department_equipment_clearance'::regclass
            """, Boolean.class);
        Integer policy = jdbc.queryForObject("""
            SELECT count(*) FROM pg_policies
             WHERE schemaname='app' AND tablename='department_equipment_clearance'
               AND policyname='p_institution' AND permissive='RESTRICTIVE'
            """, Integer.class);
        assertThat(forced).isTrue();
        assertThat(policy).isEqualTo(1);
    }

    @Test
    void noDuesViewIsSecurityInvokerAndDoesNotTreatUnavailableFeeDataAsClear() {
        String definition = jdbc.queryForObject("""
            SELECT pg_get_viewdef('app.v_fee_invoice'::regclass,true)
            """, String.class);
        assertThat(definition).contains("outstanding_amount", "student_profile_id");
        Boolean securityInvoker = jdbc.queryForObject("""
            SELECT COALESCE('security_invoker=true'=ANY(reloptions),false)
              FROM pg_class WHERE oid='app.v_fee_invoice'::regclass
            """, Boolean.class);
        assertThat(securityInvoker).isTrue();
        Boolean helperExists = jdbc.queryForObject(
                "SELECT to_regprocedure('app.fn_no_dues_department_clearance(uuid,uuid,uuid)') IS NOT NULL", Boolean.class);
        assertThat(helperExists).isTrue();
        Long missingContextBlocker = jdbc.queryForObject(
                "SELECT pending_departments FROM app.fn_no_dues_department_clearance(?,?,?)",
                Long.class, java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        assertThat(missingContextBlocker).isEqualTo(1L);
        Boolean equipmentTable = jdbc.queryForObject(
                "SELECT to_regclass('app.department_equipment_clearance') IS NOT NULL", Boolean.class);
        assertThat(equipmentTable).isTrue();
    }
}
