package com.edu360.api.institution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: a mapping of app.weekly_off_rule - "Weekly-off pattern, e.g. alternate Saturdays [CL-03]"
 * (WEEKLY_OFF_SET). One institution can hold several rules (e.g. Sunday ALL + Saturday ODD), so
 * this is append/list, not a single mutable row.
 */
@Entity
@Table(schema = "app", name = "weekly_off_rule")
@Getter
@Setter
@NoArgsConstructor
public class WeeklyOffRule {

    @Id
    @Column(name = "weekly_off_rule_id")
    private UUID weeklyOffRuleId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    /** ISO day, Monday = 1. */
    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    /** ALL, ODD or EVEN. */
    @Column(name = "occurrence", nullable = false)
    private String occurrence;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}
