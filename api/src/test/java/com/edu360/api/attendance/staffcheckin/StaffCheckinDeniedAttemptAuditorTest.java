package com.edu360.api.attendance.staffcheckin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;

class StaffCheckinDeniedAttemptAuditorTest {

    @Test
    void recordsADeniedAttemptWithTheReasonAndNumbersOnly() {
        AuditLogService audit = mock(AuditLogService.class);
        UUID staff = UUID.randomUUID();
        UUID institution = UUID.randomUUID();

        new StaffCheckinDeniedAttemptAuditor(audit).recordHardBlock(staff, institution, "OUTSIDE_GEOFENCE",
                Map.of("distance_m", 1112L, "radius_m", 200));

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        AuditEntry e = entry.getValue();
        assertThat(e.eventType()).isEqualTo(AuditEventType.DENIED_ATTEMPT);
        assertThat(e.outcome()).isEqualTo("DENIED");
        assertThat(e.actionCode()).isEqualTo("STAFF_ATTENDANCE_CHECKIN_SUBMIT");
        assertThat(e.entityType()).isEqualTo("USER_ACCOUNT");
        assertThat(e.entityId()).isEqualTo(staff);
        assertThat(e.institutionId()).isEqualTo(institution);
        assertThat(e.recordCount()).isZero();
        assertThat(e.newValues()).containsEntry("reason", "OUTSIDE_GEOFENCE")
                .containsEntry("distance_m", 1112L).containsEntry("radius_m", 200);
    }

    @Test
    void runsInItsOwnTransactionSoTheAuditSurvivesTheRefusalRollback() throws Exception {
        Transactional tx = StaffCheckinDeniedAttemptAuditor.class
                .getMethod("recordHardBlock", UUID.class, UUID.class, String.class, Map.class)
                .getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
