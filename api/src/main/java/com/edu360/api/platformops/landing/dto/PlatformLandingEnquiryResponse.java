package com.edu360.api.platformops.landing.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.platformops.landing.LandingEnquiry;

public record PlatformLandingEnquiryResponse(
        UUID enquiryId,
        String enquiryType,
        String institutionType,
        String studentRange,
        int collegesCount,
        int schoolsCount,
        int studentsEstimate,
        int staffEstimate,
        int billingCycleMonths,
        boolean includeSetupFee,
        String selectedModules,
        String migrationSupport,
        String trainingMode,
        String contactName,
        String contactEmail,
        String contactPhone,
        String institutionName,
        String notes,
        String quoteSnapshotJson,
        String status,
        String internalNotes,
        UUID handledBy,
        Integer rowVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public static PlatformLandingEnquiryResponse from(LandingEnquiry e) {
        return new PlatformLandingEnquiryResponse(
                e.getEnquiryId(),
                e.getEnquiryType(),
                e.getInstitutionType(),
                e.getStudentRange(),
                e.getCollegesCount(),
                e.getSchoolsCount(),
                e.getStudentsEstimate(),
                e.getStaffEstimate(),
                e.getBillingCycleMonths(),
                e.isIncludeSetupFee(),
                e.getSelectedModules(),
                e.getMigrationSupport(),
                e.getTrainingMode(),
                e.getContactName(),
                e.getContactEmail(),
                e.getContactPhone(),
                e.getInstitutionName(),
                e.getNotes(),
                e.getQuoteSnapshotJson(),
                e.getStatus(),
                e.getInternalNotes(),
                e.getHandledBy(),
                e.getRowVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
