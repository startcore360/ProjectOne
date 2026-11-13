package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StaffProfileRequest(
        String staffCode,
        @NotBlank @Size(min = 1, max = 150) String fullName,
        LocalDate dateOfBirth,
        @jakarta.validation.constraints.NotBlank @Email @Size(min = 3, max = 254) String officialEmail,
        @Email @Size(min = 3, max = 254) String personalEmail,
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String personalMobile,
        @Pattern(regexp = "^[0-9A-Za-z]{4}$") String nationalIdLast4,
        UUID photoFileId,
        String employmentStatus,
        LocalDate joinedOn,
        LocalDate leftOn
) {}
