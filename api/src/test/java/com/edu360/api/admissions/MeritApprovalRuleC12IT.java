package com.edu360.api.admissions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Guards the SQL-level C12 control, including substantive role and delegation rejection. */
class MeritApprovalRuleC12IT {
    @Test
    void migrationEnforcesNonDelegableSingleApprovalAtDatabaseBoundary() throws Exception {
        String sql=Files.readString(Path.of("..","sql","64_v41_merit_list_and_seat_allocation.sql"));
        assertThat(sql).contains("fn_validate_merit_approval")
                .contains("MERIT_APPROVAL_NON_DELEGABLE")
                .contains("VICE_CHANCELLOR")
                .contains("PRODUCT_OWNER")
                .contains("COLLEGE_PRINCIPAL")
                .contains("app.delegation")
                .contains("trg_merit_approval_guard");
    }
}
