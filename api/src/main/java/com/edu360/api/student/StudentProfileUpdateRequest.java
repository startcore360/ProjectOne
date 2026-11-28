package com.edu360.api.student;

import jakarta.validation.constraints.Size;

public record StudentProfileUpdateRequest(
        @Size(max = 150) String fullName,
        String dateOfBirth,
        String admissionDate,
        @Size(max = 60) String admissionId,
        @Size(max = 20) String gender,
        @Size(max = 150) String preferredName,
        @Size(max = 254) String email,
        @Size(max = 30) String mobile,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String stateRegion,
        @Size(max = 20) String postalCode,
        @Size(max = 2) String countryCode,
        String departmentId,
        String programId,
        String batchId,
        String status,
        String statusReason
) {}