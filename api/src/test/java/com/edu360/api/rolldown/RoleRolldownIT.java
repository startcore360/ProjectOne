package com.edu360.api.rolldown;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Contract regression tests for the six-hop resolver and permanent RD-G8 revocation migration. */
class RoleRolldownIT {
    @Test
    void managementAssignmentPermanentlyRevokesGbaOverridesAndHardensResolution() throws Exception {
        String migration = Files.readString(Path.of("..", "sql", "56_v41_rolldown_management_revocation.sql"));
        String resolver = Files.readString(Path.of("..", "sql", "10_v40_rolldown.sql"));

        assertThat(migration).contains("trg_revoke_gba_rolldown")
                .contains("app.gba_rolldown_revocation")
                .contains("set_by_role = 'GOVERNING_BODY_APPROVER'")
                .contains("status = 'REVOKED'")
                .contains("ROLLDOWN_GBA_REVOKED")
                .contains("GOVERNING_BODY_APPROVER");
        assertThat(migration).contains("1..6");
        assertThat(resolver).contains("FOR i IN 1..6 LOOP")
                .contains("ref.role_rolldown_default")
                .contains("app.role_rolldown_override");
    }

    @Test
    void rollbackRefusesToRestoreGbaAuthorityAfterPermanentRevocation() throws Exception {
        String rollback = Files.readString(Path.of("..", "sql",
                "56r_v41_rolldown_management_revocation_rollback.sql"));
        assertThat(rollback).contains("RD-G8 ROLLBACK BLOCKED")
                .contains("permanent revocation history exists");
    }
}
