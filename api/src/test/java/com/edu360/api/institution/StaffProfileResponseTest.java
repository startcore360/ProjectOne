package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StaffProfileResponseTest {
    @Test
    void defaultResponseMasksSensitivePersonalFields() {
        StaffProfile profile = profile();

        StaffProfileResponse response = StaffProfileResponse.from(profile);

        assertThat(response.fullName()).isEqualTo("Test Staff");
        assertThat(response.dateOfBirth()).isNull();
        assertThat(response.personalEmail()).isNull();
        assertThat(response.personalMobile()).isNull();
        assertThat(response.nationalIdLast4()).isNull();
    }

    @Test
    void explicitlyAuthorizedResponseCanIncludeSensitivePersonalFields() {
        StaffProfile profile = profile();

        StaffProfileResponse response = StaffProfileResponse.from(profile, true);

        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1990, 4, 12));
        assertThat(response.personalEmail()).isEqualTo("staff@example.invalid");
        assertThat(response.personalMobile()).isEqualTo("+919876543210");
        assertThat(response.nationalIdLast4()).isEqualTo("A123");
    }

    private static StaffProfile profile() {
        StaffProfile profile = new StaffProfile();
        profile.setId(new StaffProfileId(UUID.randomUUID(), UUID.randomUUID()));
        profile.setFullName("Test Staff");
        profile.setDateOfBirth(LocalDate.of(1990, 4, 12));
        profile.setPersonalEmail("staff@example.invalid");
        profile.setPersonalMobile("+919876543210");
        profile.setNationalIdLast4("A123");
        return profile;
    }
}
