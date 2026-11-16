package com.edu360.api.platformops.billing;

import java.time.Instant;
import java.util.UUID;
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
@Table(schema="platform",name="billing_settings")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BillingSettings {
 @Id @Column(name="billing_settings_id",nullable=false) private Integer billingSettingsId;
 @Column(name="invoice_due_days",nullable=false) private Integer invoiceDueDays;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="created_by",nullable=false) private UUID createdBy;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Column(name="updated_by",nullable=false) private UUID updatedBy;
 @Column(name="row_version",nullable=false) private Integer rowVersion;
}
