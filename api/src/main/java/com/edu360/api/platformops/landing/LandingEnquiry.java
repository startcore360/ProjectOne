package com.edu360.api.platformops.landing;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "platform", name = "landing_enquiry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LandingEnquiry {
    @Id
    @Column(name = "enquiry_id")
    private UUID enquiryId;

    @Column(name = "enquiry_type", nullable = false)
    private String enquiryType;

    @Column(name = "institution_type")
    private String institutionType;

    @Column(name = "student_range")
    private String studentRange;

    @Column(name = "colleges_count", nullable = false)
    @Builder.Default
    private int collegesCount = 0;

    @Column(name = "schools_count", nullable = false)
    @Builder.Default
    private int schoolsCount = 0;

    @Column(name = "students_estimate", nullable = false)
    @Builder.Default
    private int studentsEstimate = 0;

    @Column(name = "staff_estimate", nullable = false)
    @Builder.Default
    private int staffEstimate = 0;

    @Column(name = "billing_cycle_months", nullable = false)
    @Builder.Default
    private int billingCycleMonths = 12;

    @Column(name = "include_setup_fee", nullable = false)
    @Builder.Default
    private boolean includeSetupFee = true;

    @Column(name = "selected_modules", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private String selectedModules = "[]";

    @Column(name = "migration_support")
    private String migrationSupport;

    @Column(name = "training_mode")
    private String trainingMode;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "institution_name", nullable = false)
    private String institutionName;

    @Column(name = "notes")
    private String notes;

    @Column(name = "quote_snapshot_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String quoteSnapshotJson;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "NEW";

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private Integer rowVersion = 1;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
