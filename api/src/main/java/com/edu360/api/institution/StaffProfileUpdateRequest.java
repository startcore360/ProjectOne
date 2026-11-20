package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StaffProfileUpdateRequest(
        @Size(min = 1, max = 30) String staffCode,
        @Size(min = 1, max = 150) String fullName,
        LocalDate dateOfBirth,
        @Email @Size(min = 3, max = 254) String personalEmail,
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String personalMobile,
        @Pattern(regexp = "^[0-9A-Za-z]{4}$") String nationalIdLast4,
        UUID photoFileId,
        String employmentStatus,
        LocalDate joinedOn,
        LocalDate leftOn
) {}
