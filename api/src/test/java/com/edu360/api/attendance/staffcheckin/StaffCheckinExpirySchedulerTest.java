package com.edu360.api.attendance.staffcheckin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.tenant.TenantRepository;

class StaffCheckinExpirySchedulerTest {

    @Test
    void cutoff_isLocalMidnightInInstitutionTimezone_notUtc() {
        // 2026-11-28 23:30 IST == 18:00 UTC. The cutoff is IST midnight of that same day.
        ZonedDateTime nowIst = ZonedDateTime.of(2026, 11, 28, 23, 30, 0, 0, ZoneId.of("Asia/Kolkata"));
        Instant cutoff = StaffCheckinExpiryScheduler.localMidnightCutoff("Asia/Kolkata", nowIst.toInstant());
        assertThat(cutoff).isEqualTo(Instant.parse("2026-11-27T18:30:00Z"));
    }

    @Test
    void cutoff_rollsOverAtLocalMidnight_notAtUtcMidnight() {
        // 00:01 IST on the 28th is still the 27th in UTC; the IST day has already rolled over.
        ZonedDateTime justAfterMidnightIst = ZonedDateTime.of(2026, 11, 28, 0, 1, 0, 0, ZoneId.of("Asia/Kolkata"));
        Instant cutoff = StaffCheckinExpiryScheduler.localMidnightCutoff("Asia/Kolkata", justAfterMidnightIst.toInstant());
        assertThat(cutoff).isEqualTo(Instant.parse("2026-11-27T18:30:00Z"));
    }

    @Test
    void differentTimezones_produceDifferentCutoffsForSameInstant() {
        Instant now = Instant.parse("2026-11-28T10:00:00Z");
        assertThat(StaffCheckinExpiryScheduler.localMidnightCutoff("America/New_York", now))
                .isEqualTo(Instant.parse("2026-11-28T05:00:00Z"));
        assertThat(StaffCheckinExpiryScheduler.localMidnightCutoff("UTC", now))
                .isEqualTo(Instant.parse("2026-11-28T00:00:00Z"));
    }

    @Test
    void invalidOrBlankTimezone_fallsBackToColumnDefault() {
        Instant now = Instant.parse("2026-11-28T10:00:00Z");
        Instant ist = StaffCheckinExpiryScheduler.localMidnightCutoff("Asia/Kolkata", now);
        assertThat(StaffCheckinExpiryScheduler.localMidnightCutoff("NOT_A_ZONE", now)).isEqualTo(ist);
        assertThat(StaffCheckinExpiryScheduler.localMidnightCutoff(null, now)).isEqualTo(ist);
    }

    @Test
    void oneFailingRow_doesNotStopTheRest_andEachInstitutionUsesItsOwnZone() {
        UUID tenantId = UUID.randomUUID();
        UUID system = UUID.randomUUID();
        UUID instA = UUID.randomUUID();
        InstitutionAttendanceLocationRepository locations = mock(InstitutionAttendanceLocationRepository.class);
        StaffAttendanceCheckinRepository checkins = mock(StaffAttendanceCheckinRepository.class);
        InstitutionRepository institutions = mock(InstitutionRepository.class);
        StaffCheckInService service = mock(StaffCheckInService.class);
        InstitutionAttendanceLocation loc = InstitutionAttendanceLocation.builder().tenantId(tenantId)
                .institutionId(instA).timezone("America/New_York").build();
        when(locations.findByTenantId(tenantId)).thenReturn(List.of(loc));
        when(institutions.findByIdTenantIdAndIdInstitutionId(tenantId, instA)).thenReturn(Optional.empty());
        StaffAttendanceCheckin bad = StaffAttendanceCheckin.builder().staffAttendanceCheckinId(UUID.randomUUID()).build();
        StaffAttendanceCheckin good = StaffAttendanceCheckin.builder().staffAttendanceCheckinId(UUID.randomUUID()).build();
        Instant now = Instant.parse("2026-11-28T10:00:00Z");
        when(checkins.findByTenantIdAndInstitutionIdAndStatusAndCapturedAtBefore(
                tenantId, instA, "PROVISIONAL", Instant.parse("2026-11-28T05:00:00Z"))).thenReturn(List.of(bad, good));
        when(service.expireProvisional(eq(tenantId), eq(bad.getStaffAttendanceCheckinId()), eq(system), any(), any()))
                .thenThrow(new IllegalStateException("boom"));
        when(service.expireProvisional(eq(tenantId), eq(good.getStaffAttendanceCheckinId()), eq(system), any(), any()))
                .thenReturn(true);

        StaffCheckinExpiryScheduler scheduler = new StaffCheckinExpiryScheduler(mock(TenantRepository.class),
                mock(NamedParameterJdbcTemplate.class), institutions, locations, checkins, service);

        assertThat(scheduler.expireForTenant(tenantId, system, now)).isEqualTo(1);
        verify(service).expireProvisional(eq(tenantId), eq(good.getStaffAttendanceCheckinId()), eq(system), eq(loc), any());
    }
}
