package com.edu360.api.platformops.dto;

import java.util.UUID;

import com.edu360.api.tenant.IsolationMode;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for creating a new tenant (TENANT_CREATE).
 * Captures core tenant configuration, designated subscription plan, and
 * primary institutional representative contact details (phone and masked ID last 4).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantCreateRequest {

    @NotBlank(message = "Tenant name is required")
    @Size(min = 2, max = 200, message = "Tenant name must be between 2 and 200 characters")
    private String tenantName;

    @NotBlank(message = "Tenant type is required")
    private String tenantType; // UNIVERSITY_GROUP, STANDALONE_COLLEGE, STANDALONE_SCHOOL

    @NotNull(message = "Plan ID is required")
    private UUID planId;

    @NotBlank(message = "Subdomain is required")
    @Pattern(
            regexp = "^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$",
            message = "Subdomain must contain 2-63 lowercase alphanumeric characters and hyphens, and cannot start/end with a hyphen"
    )
    private String subdomain;

    @NotNull(message = "Isolation mode is required")
    @Builder.Default
    private IsolationMode isolationMode = IsolationMode.ISOLATED;

    @NotBlank(message = "Primary administrator full name is required")
    private String firstAdminName;

    @NotBlank(message = "Primary administrator email is required")
    @Email(message = "Primary administrator email must be a valid email address")
    private String firstAdminEmail;

    /**
     * Primary institutional contact phone number in E.164 international format (e.g., +919876543210).
     */
    @Pattern(
            regexp = "^\\+[1-9][0-9]{7,14}$",
            message = "Mobile number must be in E.164 international format (e.g., +919876543210)"
    )
    private String firstAdminMobile;

    /**
     * Masked National ID / Aadhaar last 4 digits per UIDAI guidelines and Indian DPDP compliance [R2-34].
     * Storing only the last 4 characters satisfies identity verification without illegal full-Aadhaar retention.
     */
    @Pattern(
            regexp = "^[0-9A-Za-z]{4}$",
            message = "National ID / Aadhaar verification must be exactly the last 4 digits [R2-34]"
    )
    private String firstAdminNationalIdLast4;

    @Builder.Default
    private String timeZone = "Asia/Kolkata";

    @Builder.Default
    private String dataRegion = "IN";
}
