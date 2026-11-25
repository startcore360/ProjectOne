package com.edu360.api.platformops.landing.dto;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PublicEnquiryRequest(
        @NotBlank @Pattern(regexp = "QUOTATION|DEMO") String enquiryType,
        @Size(max = 80) String institutionType,
        @Size(max = 80) String studentRange,
        @Min(0) @Max(500) int collegesCount,
        @Min(0) @Max(500) int schoolsCount,
        @Min(0) @Max(1000000) int studentsEstimate,
        @Min(0) @Max(100000) int staffEstimate,
        @Min(1) @Max(36) Integer billingCycleMonths,
        Boolean includeSetupFee,
        List<@Size(max = 80) String> selectedModules,
        @Size(max = 120) String migrationSupport,
        @Size(max = 120) String trainingMode,
        @NotBlank @Size(max = 120) String contactName,
        @NotBlank @Email @Size(max = 180) String contactEmail,
        @Size(max = 40) String contactPhone,
        @NotBlank @Size(max = 180) String institutionName,
        @Size(max = 2000) String notes
) { }
