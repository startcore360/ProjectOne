package com.edu360.api.platformops;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.auth.TemporaryPasswordGenerator;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.platformops.dataprotection.TenantRetentionPolicy;
import com.edu360.api.platformops.dataprotection.TenantRetentionPolicyRepository;
import com.edu360.api.platformops.dto.TenantProvisioningStatusResponse;
import com.edu360.api.platformops.entitlement.PlanEntitlement;
import com.edu360.api.platformops.entitlement.PlanEntitlementRepository;
import com.edu360.api.platformops.entitlement.TenantEntitlement;
import com.edu360.api.platformops.entitlement.TenantEntitlementRepository;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProvisioningException;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkId;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.identity.UsernameKind;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.LeaveApproverSettingRepository;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.dto.CreatePlanRequest;
import com.edu360.api.platformops.dto.PlanResponse;
import com.edu360.api.platformops.dto.TenantActivationRequest;
import com.edu360.api.platformops.dto.TenantCreateRequest;
import com.edu360.api.platformops.dto.TenantPageResponse;
import com.edu360.api.platformops.dto.TenantResponse;
import com.edu360.api.platformops.dto.TenantSettingsResponse;
import com.edu360.api.platformops.dto.UpdatePlanRequest;
import com.edu360.api.platformops.dto.UpdateTenantSettingsRequest;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleAction;
import com.edu360.api.rbac.RoleActionId;
import com.edu360.api.rbac.RoleActionRepository;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.RoleDefinitionStatus;
import com.edu360.api.rbac.RoleLevel;
import com.edu360.api.rbac.RoleOrigin;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.StandardRole;
import com.edu360.api.rbac.StandardRoleAction;
import com.edu360.api.rbac.StandardRoleActionRepository;
import com.edu360.api.rbac.StandardRoleRepository;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentId;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

import lombok.RequiredArgsConstructor;

/**
 * Service managing tenant lifecycle for Platform Administrators and Product Owners.
 * Implements TENANT_CREATE (Sprint 1 Story #48, D1 §26).
 *
 * Real-world rules:
 * 1. Subdomain must be unique across all tenants.
 * 2. Tenant starts in TRIAL state with a 30-day initial evaluation period.
 * 3. Default isolation mode is ISOLATED (dedicated DB reference), with SHARED permitted.
 * 4. Limits (users, storage, SMS, API calls) are inherited from the selected Plan.
 * 5. Provisions the dedicated Keycloak realm and registers the frontend client.
 * 6. Contact phone (+91 E.164) and masked national ID (last 4 digits per UIDAI/DPDP [R2-34])
 *    are recorded for compliance without storing full sensitive identity numbers.
 */
@Service
public class TenantManagementService {

    private static final Logger log = LoggerFactory.getLogger(TenantManagementService.class);

    private static final Set<String> ALLOWED_TENANT_TYPES = Set.of(
            "UNIVERSITY_GROUP", "STANDALONE_COLLEGE", "STANDALONE_SCHOOL"
    );

    private static final Set<String> RESERVED_SUBDOMAINS = Set.of(
            "platform", "www", "api", "admin", "auth", "keycloak", "mail", "static", "status"
    );

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final LeaveApproverSettingRepository leaveApproverSettingRepository;
    private final InstitutionRepository institutionRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserIdentityLinkRepository userIdentityLinkRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final StandardRoleRepository standardRoleRepository;
    private final StandardRoleActionRepository standardRoleActionRepository;
    private final RoleActionRepository roleActionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter;
    private final com.edu360.api.platformops.billing.InvoiceRepository invoiceRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.edu360.api.config.TenantIssuerAuthenticationManagerResolver tenantIssuerResolver;

    /** B3 / I-06: a new tenant realm gets the current security policy and sign-in baseline. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.edu360.api.platformops.security.RealmSecuritySyncService realmSecuritySyncService;

    /** Additive E1-S2 dependencies; the existing public constructor remains unchanged. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TenantProvisioningService tenantProvisioningService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TenantLocalProvisioningService tenantLocalProvisioningService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TenantRetentionPolicyRepository tenantRetentionPolicyRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PlanEntitlementRepository planEntitlementRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TenantEntitlementRepository tenantEntitlementRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PlatformUserRepository platformUserRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PlatformNotificationService platformNotificationService;

    @org.springframework.beans.factory.annotation.Autowired
    public TenantManagementService(
            TenantRepository tenantRepository,
            PlanRepository planRepository,
            KeycloakAdminClient keycloakAdminClient,
            LeaveApproverSettingRepository leaveApproverSettingRepository,
            InstitutionRepository institutionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserAccountRepository userAccountRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserIdentityLinkRepository userIdentityLinkRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RoleDefinitionRepository roleDefinitionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) StandardRoleRepository standardRoleRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) StandardRoleActionRepository standardRoleActionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RoleActionRepository roleActionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserRoleAssignmentRepository userRoleAssignmentRepository,
            com.edu360.api.platformops.billing.InvoiceRepository invoiceRepository,
            com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter
    ) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.leaveApproverSettingRepository = leaveApproverSettingRepository;
        this.institutionRepository = institutionRepository;
        this.userAccountRepository = userAccountRepository;
        this.userIdentityLinkRepository = userIdentityLinkRepository;
        this.roleDefinitionRepository = roleDefinitionRepository;
        this.standardRoleRepository = standardRoleRepository;
        this.standardRoleActionRepository = standardRoleActionRepository;
        this.roleActionRepository = roleActionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.platformAuditWriter = platformAuditWriter;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Lists active subscription plans for selection during tenant creation.
     * If no plans are present in the database, seeds default active tiers.
     */
    @Transactional
    public List<PlanResponse> listPlans() {
        List<Plan> plans = planRepository.findByStatusOrderByPlanNameAsc("ACTIVE");
        if (plans.isEmpty()) {
            plans = seedDefaultPlans();
        }
        return plans.stream().map(PlanResponse::from).toList();
    }

    /**
     * Creates a new tenant in TRIAL state.
     */
        public TenantResponse createTenant(TenantCreateRequest request, PlatformPrincipal principal) {
        if (request.getIsolationMode() == IsolationMode.ISOLATED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ISOLATION_NOT_AVAILABLE",
                    "Dedicated-database tenant isolation is not available; use SHARED isolation.");
        }
        String normalizedSubdomain = request.getSubdomain() == null ? ""
                : request.getSubdomain().toLowerCase().trim();
        if (!normalizedSubdomain.matches("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBDOMAIN_INVALID",
                    "Subdomain must contain 3-63 lowercase alphanumeric characters or hyphens and cannot start or end with a hyphen.");
        }
        if (RESERVED_SUBDOMAINS.contains(normalizedSubdomain)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SUBDOMAIN_RESERVED",
                    "The subdomain '" + normalizedSubdomain + "' is reserved by the platform.");
        }

        // Unit tests that instantiate this service directly bypass Spring field injection.
        // Production wiring always supplies the coordinator; retain the pre-coordinator path only
        // for those manually constructed service fixtures.
        if (tenantProvisioningService == null) {
            return createTenantLegacy(request, principal);
        }

        if (tenantRepository.findBySubdomain(normalizedSubdomain).isPresent()) {
            throw new DuplicateResourceException("A tenant with subdomain '" + normalizedSubdomain + "' already exists");
        }
        if (!ALLOWED_TENANT_TYPES.contains(request.getTenantType())) {
            throw new IllegalArgumentException("Invalid tenant type: " + request.getTenantType()
                    + ". Must be one of: " + ALLOWED_TENANT_TYPES);
        }

        Plan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Plan with ID " + request.getPlanId() + " does not exist"));

        if (tenantProvisioningService == null || tenantLocalProvisioningService == null
                || tenantRetentionPolicyRepository == null || planEntitlementRepository == null
                || tenantEntitlementRepository == null) {
            throw new IllegalStateException("Tenant provisioning dependencies are not available.");
        }

        UUID tenantId = UUID.randomUUID();
        UUID adminUserAccountId = UUID.randomUUID();
        String realmName = normalizedSubdomain + "-realm";
        String temporaryPassword = TemporaryPasswordGenerator.generate();

        Instant now = Instant.now();
        Instant trialEndsAt = now.plus(Duration.ofDays(30));
        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .tenantName(request.getTenantName().trim())
                .tenantType(request.getTenantType())
                .planId(plan.getPlanId())
                .state(TenantState.CREATED)
                .isolationMode(IsolationMode.SHARED)
                .subdomain(normalizedSubdomain)
                .timeZone(request.getTimeZone() != null ? request.getTimeZone() : "Asia/Kolkata")
                .dataRegion(request.getDataRegion() != null ? request.getDataRegion() : "IN")
                .trialEndsAt(trialEndsAt)
                .userLimit(plan.getUserLimit())
                .storageLimitGb(plan.getStorageLimitGb())
                .smsLimit(plan.getSmsLimit())
                .apiLimit(plan.getApiLimit())
                .keycloakRealm(realmName)
                .analyticsOptOut(false)
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        ProvisioningInputs inputs = new ProvisioningInputs(request.getTenantType(), request.getTimeZone(),
                request.getRetentionMonths(), request.getBackupRetentionDays(), adminUserAccountId,
                request.getFirstAdminName().trim(), request.getFirstAdminEmail().toLowerCase().trim(),
                request.getFirstAdminMobile() != null && !request.getFirstAdminMobile().isBlank()
                        ? request.getFirstAdminMobile().trim() : null,
                temporaryPassword);
        tenantRepository.save(tenant);
        TenantProvisioningStatusResponse provisioningStatus = runProvisioning(tenant, plan, inputs, principal, false);
        failIfNotProvisioned(tenantId, provisioningStatus, principal);
        Tenant provisionedTenant = markProvisioned(tenant, principal);
        platformAuditWriter.record(principal, tenantId, "TENANT_CREATED", "TENANT_CREATE", "TENANT", tenantId);

        return TenantResponse.from(
                provisionedTenant,
                plan.getPlanName(),
                request.getFirstAdminEmail(),
                request.getFirstAdminMobile(),
                request.getFirstAdminNationalIdLast4(),
                temporaryPassword,
                adminUserAccountId
        );
    }

    /**
     * E1-S2: what the setup steps need. Everything here is also kept on the tenant row or on the
     * first admin's account (saved PENDING_ACTIVATION by P01), so a retry can rebuild the steps.
     * Retention values are only needed while P09 is not DONE.
     */
    private record ProvisioningInputs(String tenantType, String timeZone, Integer retentionMonths,
                                      Integer backupRetentionDays, UUID adminUserAccountId, String firstAdminName,
                                      String firstAdminEmail, String firstAdminMobile, String temporaryPassword) {
    }

    private TenantProvisioningStatusResponse runProvisioning(Tenant tenant, Plan plan, ProvisioningInputs inputs,
                                                             PlatformPrincipal principal, boolean retry) {
        Map<String, TenantProvisioningService.StepAction> actions = provisioningSteps(tenant, plan, inputs, principal);
        TenantContext.set(tenant.getTenantId(), IsolationMode.SHARED, null, "ALL", Set.of(), null, null);
        try {
            return retry
                    ? tenantProvisioningService.retry(tenant.getTenantId(), principal.platformUserId(), actions)
                    : tenantProvisioningService.provision(tenant.getTenantId(), principal.platformUserId(), actions);
        } finally {
            TenantContext.clear();
        }
    }

    /** A failed setup leaves the tenant CREATED, alerts the Platform Admins and answers 503. */
    private void failIfNotProvisioned(UUID tenantId, TenantProvisioningStatusResponse status, PlatformPrincipal principal) {
        if ("DONE".equals(status.overallStatus())) {
            return;
        }
        notifyTenantProvisioningFailure(tenantId, principal.platformUserId());
        boolean keycloakUnavailable = status.steps().stream()
                .anyMatch(step -> "KEYCLOAK_UNAVAILABLE".equals(step.lastErrorCode()));
        if (keycloakUnavailable) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "KEYCLOAK_UNAVAILABLE",
                    "Tenant setup could not reach or configure the identity service. The tenant remains in setup.");
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVISIONING_FAILED",
                "Tenant setup did not complete. The tenant remains in setup and can be retried.");
    }

    private Tenant markProvisioned(Tenant tenant, PlatformPrincipal principal) {
        tenant.setState(TenantState.TRIAL);
        tenant.setUpdatedAt(Instant.now());
        tenant.setUpdatedBy(principal.platformUserId());
        return tenantRepository.save(tenant);
    }

    /** E1-S2: GET /platform/tenants/{id}/provisioning - the seven setup steps in run order. */
    public TenantProvisioningStatusResponse getProvisioningStatus(UUID tenantId) {
        if (tenantProvisioningService == null) {
            throw new IllegalStateException("Tenant provisioning is not available.");
        }
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found"));
        return tenantProvisioningService.getStatus(tenantId);
    }

    /**
     * E1-S2: POST /platform/tenants/{id}/provisioning/retry - re-runs setup from the first step that
     * is not DONE. A retry that still has to create the first admin issues a new temporary password,
     * shown once in this answer (PD-10); the original one was never stored.
     */
    public TenantResponse retryProvisioning(UUID tenantId, Integer retentionMonths, Integer backupRetentionDays,
                                            PlatformPrincipal principal) {
        if (tenantProvisioningService == null || userAccountRepository == null) {
            throw new IllegalStateException("Tenant provisioning is not available.");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found"));
        if (tenant.getState() != TenantState.CREATED) {
            throw new ApiException(HttpStatus.CONFLICT, "TENANT_ALREADY_PROVISIONED",
                    "This tenant's setup is already complete.");
        }
        Plan plan = planRepository.findById(tenant.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("The tenant's plan no longer exists"));
        TenantProvisioningStatusResponse before = tenantProvisioningService.getStatus(tenantId);
        boolean adminStepPending = before.steps().stream()
                .anyMatch(step -> "P07_FIRST_ADMIN".equals(step.stepCode()) && !"DONE".equals(step.status()));

        UserAccount admin;
        TenantContext.set(tenantId, IsolationMode.SHARED, null, "ALL", Set.of(), null, null);
        try {
            admin = userAccountRepository.findByIdTenantIdAndStatus(tenantId, UserAccountStatus.PENDING_ACTIVATION)
                    .stream().findFirst()
                    .or(() -> userAccountRepository.findByIdTenantIdAndStatus(tenantId, UserAccountStatus.ACTIVE)
                            .stream().findFirst())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "FIRST_ADMIN_UNKNOWN",
                            "This tenant was set up before setup retry existed and has no recorded first admin. "
                                    + "Archive it and create it again."));
        } finally {
            TenantContext.clear();
        }

        String temporaryPassword = adminStepPending ? TemporaryPasswordGenerator.generate() : null;
        ProvisioningInputs inputs = new ProvisioningInputs(tenant.getTenantType(), tenant.getTimeZone(),
                retentionMonths, backupRetentionDays, admin.getId().getUserAccountId(), admin.getDisplayName(),
                admin.getEmail(), admin.getMobile(), temporaryPassword);
        TenantProvisioningStatusResponse status = runProvisioning(tenant, plan, inputs, principal, true);
        failIfNotProvisioned(tenantId, status, principal);
        Tenant provisionedTenant = markProvisioned(tenant, principal);
        platformAuditWriter.record(principal, tenantId, "TENANT_PROVISIONING_RETRIED", "TENANT_CREATE", "TENANT", tenantId);
        return TenantResponse.from(provisionedTenant, plan.getPlanName(), admin.getEmail(), admin.getMobile(), null,
                temporaryPassword, admin.getId().getUserAccountId());
    }

    /** The seven ordered, idempotent setup steps (E1-S2). Built fresh for a create and for each retry. */
    private Map<String, TenantProvisioningService.StepAction> provisioningSteps(
            Tenant tenant, Plan plan, ProvisioningInputs in, PlatformPrincipal principal) {
        UUID tenantId = tenant.getTenantId();
        String realmName = tenant.getKeycloakRealm();
        UUID adminUserAccountId = in.adminUserAccountId();
        String[] keycloakAdminUserId = new String[1];
        UUID[] identityLinkId = new UUID[1];
        Map<String, TenantProvisioningService.StepAction> actions = new LinkedHashMap<>();

        actions.put("P01_REGISTRY", new TenantProvisioningService.StepAction() {
            @Override
            public void execute() {
                tenantRepository.save(tenant);
                tenantLocalProvisioningService.registerTenant(tenantId, in.tenantType(), in.timeZone());
                // Kept now, PENDING_ACTIVATION, so a later failure can be retried without the request.
                if (userAccountRepository != null
                        && !userAccountRepository.existsById(new UserAccountId(tenantId, adminUserAccountId))) {
                    userAccountRepository.save(UserAccount.builder()
                            .id(new UserAccountId(tenantId, adminUserAccountId))
                            .userType(UserType.STAFF)
                            .displayName(in.firstAdminName())
                            .email(in.firstAdminEmail())
                            .mobile(in.firstAdminMobile())
                            .status(UserAccountStatus.PENDING_ACTIVATION)
                            .createdBy(principal.platformUserId())
                            .updatedBy(principal.platformUserId())
                            .build());
                }
            }
        });

        actions.put("P09_RETENTION", () -> {
            if (in.retentionMonths() == null || in.backupRetentionDays() == null) {
                throw new IllegalStateException("RETENTION_INPUT_REQUIRED: give retentionMonths and backupRetentionDays to finish setup.");
            }
            int backupRetentionMonths = Math.floorDiv(in.backupRetentionDays() + 29, 30);
            List<String> dataClasses = List.of(
                    "STRUCTURE", "PERSON", "ACADEMIC_RECORD", "FINANCIAL", "COMMUNICATION",
                    "STUDENT_NOTE", "GOVERNANCE", "APPROVAL", "BY_OWNER", "TENANT_LIFETIME",
                    "AUDIT", "BACKUP");
            Instant policyTime = Instant.now();
            for (String dataClass : dataClasses) {
                int months = ("AUDIT".equals(dataClass) || "BACKUP".equals(dataClass))
                        ? backupRetentionMonths : in.retentionMonths();
                TenantRetentionPolicy policy = tenantRetentionPolicyRepository
                        .findByTenantIdAndDataClassCodeAndStatus(tenantId, dataClass, "ACTIVE")
                        .orElseGet(() -> TenantRetentionPolicy.builder()
                                .tenantRetentionPolicyId(UUID.randomUUID())
                                .tenantId(tenantId)
                                .dataClassCode(dataClass)
                                .effectiveFrom(policyTime)
                                .status("ACTIVE")
                                .createdAt(policyTime)
                                .createdBy(principal.platformUserId())
                                .updatedAt(policyTime)
                                .updatedBy(principal.platformUserId())
                                .rowVersion(1)
                                .build());
                policy.setRetentionMonths(months);
                policy.setUpdatedAt(policyTime);
                policy.setUpdatedBy(principal.platformUserId());
                tenantRetentionPolicyRepository.save(policy);
            }
        });

        actions.put("P05_ENTITLEMENTS", () -> {
            Set<String> existingKeys = new java.util.HashSet<>();
            tenantEntitlementRepository.findByTenantId(tenantId).forEach(entitlement ->
                    existingKeys.add(entitlement.getModuleCode() + "|" + Objects.toString(entitlement.getFeatureCode(), "")));
            Instant entitlementTime = Instant.now();
            for (PlanEntitlement planEntitlement : planEntitlementRepository.findByPlanId(plan.getPlanId())) {
                String key = planEntitlement.getModuleCode() + "|"
                        + Objects.toString(planEntitlement.getFeatureCode(), "");
                if (existingKeys.add(key)) {
                    tenantEntitlementRepository.save(TenantEntitlement.builder()
                            .tenantEntitlementId(UUID.randomUUID())
                            .tenantId(tenantId)
                            .moduleCode(planEntitlement.getModuleCode())
                            .featureCode(planEntitlement.getFeatureCode())
                            .enabled(planEntitlement.isEnabled())
                            .createdAt(entitlementTime)
                            .createdBy(principal.platformUserId())
                            .updatedAt(entitlementTime)
                            .updatedBy(principal.platformUserId())
                            .rowVersion(1)
                            .build());
                }
            }
        });

        actions.put("P06_IDENTITY_REALM", new TenantProvisioningService.StepAction() {
            @Override
            public void execute() {
                try {
                    keycloakAdminClient.provisionRealm(realmName, tenant.getTenantName());
                    log.info("Provisioned Keycloak realm '{}' for tenant '{}'", realmName, tenant.getTenantName());
                } catch (RuntimeException ex) {
                    try {
                        keycloakAdminClient.deleteRealm(realmName);
                    } catch (RuntimeException cleanupFailure) {
                        log.warn("Could not clean up partially provisioned realm '{}'", realmName);
                    }
                    if (ex instanceof KeycloakProvisioningException keycloakException) {
                        throw keycloakException;
                    }
                    throw new KeycloakProvisioningException(
                            "Could not provision Keycloak realm '" + realmName + "'.", ex);
                }
            }

            @Override
            public void compensate() {
                keycloakAdminClient.deleteRealm(realmName);
            }
        });

        actions.put("P10_REALM_POLICY", () -> {
            if (realmSecuritySyncService == null) {
                throw new KeycloakProvisioningException("Realm security policy service is unavailable.");
            }
            try {
                realmSecuritySyncService.applyToRealm(realmName);
            } catch (RuntimeException ex) {
                throw new KeycloakProvisioningException(
                        "Could not apply the security policy to realm '" + realmName + "'.", ex);
            }
        });

        actions.put("P07_FIRST_ADMIN", new TenantProvisioningService.StepAction() {
            @Override
            public void execute() {
                try {
                    if (in.temporaryPassword() == null) {
                        throw new IllegalStateException("A temporary password is required to create the first admin.");
                    }
                    keycloakAdminUserId[0] = keycloakAdminClient.provisionUser(
                            realmName, in.firstAdminName(), in.firstAdminEmail(), in.temporaryPassword(), true);
                    if (keycloakAdminUserId[0] == null || keycloakAdminUserId[0].isBlank()) {
                        throw new KeycloakProvisioningException(
                                "Keycloak did not return a valid user id for the tenant first admin.");
                    }

                    if (userAccountRepository == null || userIdentityLinkRepository == null) {
                        throw new IllegalStateException("Tenant identity repositories are not available.");
                    }
                    UserAccount account = userAccountRepository.findById(new UserAccountId(tenantId, adminUserAccountId))
                            .orElseGet(() -> UserAccount.builder()
                                    .id(new UserAccountId(tenantId, adminUserAccountId))
                                    .userType(UserType.STAFF)
                                    .displayName(in.firstAdminName())
                                    .email(in.firstAdminEmail())
                                    .mobile(in.firstAdminMobile())
                                    .createdBy(principal.platformUserId())
                                    .build());
                    account.setStatus(UserAccountStatus.ACTIVE);
                    account.setUpdatedBy(principal.platformUserId());
                    userAccountRepository.save(account);
                    identityLinkId[0] = UUID.randomUUID();
                    userIdentityLinkRepository.save(UserIdentityLink.builder()
                            .id(new UserIdentityLinkId(tenantId, identityLinkId[0]))
                            .userAccountId(adminUserAccountId)
                            .identityRealm(realmName)
                            .identitySubject(keycloakAdminUserId[0])
                            .usernameKind(UsernameKind.EMAIL)
                            .status(IdentityLinkStatus.ACTIVE)
                            .createdBy(principal.platformUserId())
                            .updatedBy(principal.platformUserId())
                            .build());
                } catch (RuntimeException ex) {
                    if (keycloakAdminUserId[0] != null && !keycloakAdminUserId[0].isBlank()) {
                        try {
                            keycloakAdminClient.deleteUser(realmName, keycloakAdminUserId[0]);
                        } catch (RuntimeException cleanupFailure) {
                            log.warn("Could not clean up partially provisioned first admin in realm '{}'", realmName);
                        }
                        keycloakAdminUserId[0] = null;
                    }
                    throw ex;
                }
            }

            @Override
            public void compensate() {
                if (keycloakAdminUserId[0] != null && !keycloakAdminUserId[0].isBlank()) {
                    keycloakAdminClient.deleteUser(realmName, keycloakAdminUserId[0]);
                }
                if (identityLinkId[0] != null && userIdentityLinkRepository != null) {
                    UserIdentityLinkId linkId = new UserIdentityLinkId(tenantId, identityLinkId[0]);
                    if (userIdentityLinkRepository.existsById(linkId)) {
                        userIdentityLinkRepository.deleteById(linkId);
                    }
                }
                // The account itself belongs to P01 and stays, back to PENDING_ACTIVATION, for a retry.
                if (userAccountRepository != null) {
                    userAccountRepository.findById(new UserAccountId(tenantId, adminUserAccountId)).ifPresent(account -> {
                        account.setStatus(UserAccountStatus.PENDING_ACTIVATION);
                        userAccountRepository.save(account);
                    });
                }
            }
        });

        actions.put("P04_ROLES_SEED", () -> {
            if (roleDefinitionRepository == null || standardRoleActionRepository == null
                    || roleActionRepository == null || userRoleAssignmentRepository == null) {
                throw new IllegalStateException("Tenant standard-role repositories are not available.");
            }
            String roleCode = "STANDALONE_SCHOOL".equals(in.tenantType()) ? "SCHOOL_ADMIN"
                    : "STANDALONE_COLLEGE".equals(in.tenantType()) ? "COLLEGE_ADMIN"
                    : "UNIVERSITY_ADMIN";
            RoleDefinition roleDefinition = roleDefinitionRepository.findByIdTenantIdAndRoleCode(tenantId, roleCode)
                    .orElseGet(() -> {
                        StandardRole standardRole = standardRoleRepository != null
                                ? standardRoleRepository.findById(roleCode).orElse(null) : null;
                        return roleDefinitionRepository.save(RoleDefinition.builder()
                                .id(new RoleDefinitionId(tenantId, UUID.randomUUID()))
                                .roleCode(roleCode)
                                .roleName(standardRole != null ? standardRole.getRoleName() : "Tenant Administrator")
                                .roleOrigin(RoleOrigin.STANDARD)
                                .roleLevel(standardRole != null ? standardRole.getRoleLevel() : RoleLevel.TENANT)
                                .baseStandardRole(roleCode)
                                .customScope(null)
                                .sensitiveRole(false)
                                .status(RoleDefinitionStatus.ACTIVE)
                                .createdBy(principal.platformUserId())
                                .updatedBy(principal.platformUserId())
                                .build());
                    });

            UUID roleDefinitionId = roleDefinition.getId().getRoleDefinitionId();
            for (StandardRoleAction standardAction : standardRoleActionRepository.findByRoleCode(roleCode)) {
                if (!roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(
                        tenantId, roleDefinitionId, standardAction.getActionCode())) {
                    roleActionRepository.save(RoleAction.builder()
                            .id(new RoleActionId(tenantId, UUID.randomUUID()))
                            .roleDefinitionId(roleDefinitionId)
                            .actionCode(standardAction.getActionCode())
                            .accessMode("SHARED")
                            .createdBy(principal.platformUserId())
                            .updatedBy(principal.platformUserId())
                            .build());
                }
            }

            boolean assigned = userRoleAssignmentRepository
                    .findByIdTenantIdAndUserAccountIdAndStatus(tenantId, adminUserAccountId, AssignmentStatus.ACTIVE)
                    .stream().anyMatch(assignment -> roleDefinitionId.equals(assignment.getRoleDefinitionId()));
            if (!assigned) {
                userRoleAssignmentRepository.save(UserRoleAssignment.builder()
                        .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                        .userAccountId(adminUserAccountId)
                        .roleDefinitionId(roleDefinitionId)
                        .scopeType(ScopeType.TENANT)
                        .effectiveFrom(Instant.now())
                        .status(AssignmentStatus.ACTIVE)
                        .createdBy(principal.platformUserId())
                        .updatedBy(principal.platformUserId())
                        .build());
            }
        });

        return actions;
    }

    private void notifyTenantProvisioningFailure(UUID tenantId, UUID actorPlatformUserId) {
        if (platformUserRepository == null || platformNotificationService == null) {
            return;
        }
        List<PlatformUser> admins = platformUserRepository.findAllByPlatformRoleAndStatus(
                PlatformRole.PLATFORM_ADMIN, PlatformUserStatus.ACTIVE);
        for (PlatformUser admin : admins) {
            try {
                platformNotificationService.notify(
                        admin.getPlatformUserId(),
                        "PLATFORM_ALERT",
                        "Tenant setup failed",
                        "Provisioning did not complete for tenant " + tenantId + ".",
                        "TENANT",
                        tenantId,
                        // platform_notification allows NORMAL/SECURITY/EMERGENCY only; "HIGH" made
                        // every setup-failure alert fail to save, so Platform Admins never saw it.
                        "NORMAL",
                        actorPlatformUserId);
            } catch (RuntimeException ex) {
                log.warn("Could not notify Platform Admin {} about tenant provisioning failure", admin.getPlatformUserId());
            }
        }
    }

private TenantResponse createTenantLegacy(TenantCreateRequest request, PlatformPrincipal principal) {
        if (request.getIsolationMode() == IsolationMode.ISOLATED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ISOLATION_NOT_AVAILABLE",
                    "Dedicated-database tenant isolation is not available; use SHARED isolation.");
        }
        String normalizedSubdomain = request.getSubdomain().toLowerCase().trim();
        if (!normalizedSubdomain.matches("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$")) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SUBDOMAIN_INVALID",
                    "The subdomain must be 3-63 lowercase letters, numbers, or hyphens, and cannot start or end with a hyphen.");
        }

        // 1. Reject platform-owned labels on both the current and legacy creation paths.
        if (RESERVED_SUBDOMAINS.contains(normalizedSubdomain)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SUBDOMAIN_RESERVED",
                    "The subdomain '" + normalizedSubdomain + "' is reserved by the platform.");
        }
        // 2. Check subdomain uniqueness.
        if (tenantRepository.findBySubdomain(normalizedSubdomain).isPresent()) {
            throw new DuplicateResourceException("A tenant with subdomain '" + normalizedSubdomain + "' already exists");
        }

        // 2. Validate tenant type
        if (!ALLOWED_TENANT_TYPES.contains(request.getTenantType())) {
            throw new IllegalArgumentException("Invalid tenant type: " + request.getTenantType()
                    + ". Must be one of: " + ALLOWED_TENANT_TYPES);
        }

        // 3. Validate plan exists and is active
        Plan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Plan with ID " + request.getPlanId() + " does not exist"));

        UUID tenantId = UUID.randomUUID();
        String realmName = normalizedSubdomain + "-realm";

        // 4. Provision Keycloak realm and client
        try {
            keycloakAdminClient.provisionRealm(realmName, request.getTenantName());
            log.info("Provisioned Keycloak realm '{}' for tenant '{}'", realmName, request.getTenantName());
        } catch (KeycloakProvisioningException ex) {
            log.warn("Keycloak realm provisioning skipped or non-fatal in current environment: {}", ex.getMessage());
        }

        // B3 / I-06 (REG-I06-2): lockout, password policy, two-step flow and password-check client
        // from the current security policy. A realm without them must not be used, so a failure
        // here fails the tenant creation (KeycloakProvisioningException -> 503).
        if (realmSecuritySyncService != null) {
            try {
                realmSecuritySyncService.applyToRealm(realmName);
            } catch (RuntimeException ex) {
                throw new KeycloakProvisioningException(
                        "Could not apply the security policy to realm '" + realmName + "': " + ex.getMessage(), ex);
            }
        }

        // 5. Construct and persist Tenant entity
        Instant now = Instant.now();
        Instant trialEndsAt = now.plus(Duration.ofDays(30));

        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .tenantName(request.getTenantName().trim())
                .tenantType(request.getTenantType())
                .planId(plan.getPlanId())
                .state(TenantState.TRIAL)
                .isolationMode(request.getIsolationMode())
                .subdomain(normalizedSubdomain)
                .timeZone(request.getTimeZone() != null ? request.getTimeZone() : "Asia/Kolkata")
                .dataRegion(request.getDataRegion() != null ? request.getDataRegion() : "IN")
                .trialEndsAt(trialEndsAt)
                .userLimit(plan.getUserLimit())
                .storageLimitGb(plan.getStorageLimitGb())
                .smsLimit(plan.getSmsLimit())
                .apiLimit(plan.getApiLimit())
                .keycloakRealm(realmName)
                .analyticsOptOut(false)
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);

        // CL-08 / WF-02: retention is supplied at creation; no system duration is inferred.
        if (entityManager != null) {
            int backupRetentionMonths = Math.floorDiv(request.getBackupRetentionDays() + 29, 30);
            List<String> retainedDataClasses = List.of(
                "STRUCTURE", "PERSON", "ACADEMIC_RECORD", "FINANCIAL", "COMMUNICATION",
                "STUDENT_NOTE", "GOVERNANCE", "APPROVAL", "BY_OWNER", "TENANT_LIFETIME",
                "AUDIT", "BACKUP"
        );
            for (String dataClassCode : retainedDataClasses) {
                int retentionMonths = ("AUDIT".equals(dataClassCode) || "BACKUP".equals(dataClassCode))
                    ? backupRetentionMonths : request.getRetentionMonths();
                entityManager.createNativeQuery(
                    "INSERT INTO platform.tenant_retention_policy "
                        + "(tenant_retention_policy_id, tenant_id, data_class_code, retention_months, effective_from, status, "
                    + "created_at, created_by, updated_at, updated_by, row_version) "
                    + "VALUES (:id, :tenantId, :dataClass, :months, :effectiveFrom, 'ACTIVE', "
                    + ":createdAt, :actor, :updatedAt, :actor, 1)")
                        .setParameter("id", UUID.randomUUID())
                    .setParameter("tenantId", tenantId)
                    .setParameter("dataClass", dataClassCode)
                    .setParameter("months", retentionMonths)
                    .setParameter("effectiveFrom", now)
                    .setParameter("createdAt", now)
                    .setParameter("updatedAt", now)
                    .setParameter("actor", principal.platformUserId())
                        .executeUpdate();
            }
        }

        // 5b. Provision First Tenant Admin
        UUID adminUserAccountId = UUID.randomUUID();
        String tempPassword = "Edu@" + UUID.randomUUID().toString().substring(0, 8) + "!";
        String keycloakUserId = keycloakAdminClient.provisionUser(
                realmName,
                request.getFirstAdminName(),
                request.getFirstAdminEmail().toLowerCase().trim(),
                tempPassword,
                true
        );
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new KeycloakProvisioningException("Keycloak did not return a valid user id for tenant first admin");
        }
        log.info("Provisioned first tenant admin Keycloak account {} for '{}' in realm '{}'",
                keycloakUserId, request.getFirstAdminEmail(), realmName);

        platformAuditWriter.record(principal, savedTenant.getTenantId(), "TENANT_CREATED", "TENANT_CREATE", "TENANT", savedTenant.getTenantId());

        if (entityManager != null) {
            entityManager.createNativeQuery("SET app.tenant_id = '" + tenantId + "'").executeUpdate();
            entityManager.createNativeQuery("SET app.institution_scope = 'ALL'").executeUpdate();
            entityManager.createNativeQuery(
                    "INSERT INTO app.tenant_local (tenant_id, tenant_type, time_zone, provisioned_at) VALUES (:tid, :ttype, :tz, now()) ON CONFLICT (tenant_id) DO NOTHING")
                    .setParameter("tid", tenantId)
                    .setParameter("ttype", request.getTenantType())
                    .setParameter("tz", request.getTimeZone() != null ? request.getTimeZone() : "Asia/Kolkata")
                    .executeUpdate();
        }
        TenantContext.set(tenantId, request.getIsolationMode(), null, "ALL", Set.of(), null, null);
        try {
            if (userAccountRepository != null) {
                UserAccount adminAccount = UserAccount.builder()
                        .id(new UserAccountId(tenantId, adminUserAccountId))
                        .userType(UserType.STAFF)
                        .displayName(request.getFirstAdminName().trim())
                        .email(request.getFirstAdminEmail().toLowerCase().trim())
                        .mobile(request.getFirstAdminMobile() != null && !request.getFirstAdminMobile().isBlank()
                                ? request.getFirstAdminMobile().trim() : null)
                        .status(UserAccountStatus.ACTIVE)
                        .createdBy(principal.platformUserId())
                        .updatedBy(principal.platformUserId())
                        .build();
                userAccountRepository.save(adminAccount);

                if (userIdentityLinkRepository != null) {
                    UserIdentityLink link = UserIdentityLink.builder()
                            .id(new UserIdentityLinkId(tenantId, UUID.randomUUID()))
                            .userAccountId(adminUserAccountId)
                            .identityRealm(realmName)
                            .identitySubject(keycloakUserId)
                            .usernameKind(UsernameKind.EMAIL)
                            .status(IdentityLinkStatus.ACTIVE)
                            .createdBy(principal.platformUserId())
                            .updatedBy(principal.platformUserId())
                            .build();
                    userIdentityLinkRepository.save(link);
                }

                // Assign standard role
                String roleCode = "STANDALONE_SCHOOL".equals(request.getTenantType()) ? "SCHOOL_ADMIN"
                        : "STANDALONE_COLLEGE".equals(request.getTenantType()) ? "COLLEGE_ADMIN"
                        : "UNIVERSITY_ADMIN";

                if (roleDefinitionRepository != null) {
                    RoleDefinition roleDef = roleDefinitionRepository.findByIdTenantIdAndRoleCode(tenantId, roleCode)
                            .orElseGet(() -> {
                                StandardRole sr = standardRoleRepository != null ? standardRoleRepository.findById(roleCode).orElse(null) : null;
                                RoleDefinition rd = RoleDefinition.builder()
                                        .id(new RoleDefinitionId(tenantId, UUID.randomUUID()))
                                        .roleCode(roleCode)
                                        .roleName(sr != null ? sr.getRoleName() : "Tenant Administrator")
                                        .roleOrigin(RoleOrigin.STANDARD)
                                        .roleLevel(sr != null ? sr.getRoleLevel() : RoleLevel.TENANT)
                                        .baseStandardRole(roleCode)
                                        .customScope(null)
                                        .sensitiveRole(false)
                                        .status(RoleDefinitionStatus.ACTIVE)
                                        .createdBy(principal.platformUserId())
                                        .updatedBy(principal.platformUserId())
                                        .build();
                                return roleDefinitionRepository.save(rd);
                            });

                    if (standardRoleActionRepository != null && roleActionRepository != null) {
                        List<StandardRoleAction> templateActions = standardRoleActionRepository.findByRoleCode(roleCode);
                        UUID roleDefId = roleDef.getId().getRoleDefinitionId();
                        for (StandardRoleAction ta : templateActions) {
                            boolean exists = roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(
                                    tenantId, roleDefId, ta.getActionCode());
                            if (!exists) {
                                roleActionRepository.save(RoleAction.builder()
                                        .id(new RoleActionId(tenantId, UUID.randomUUID()))
                                        .roleDefinitionId(roleDefId)
                                        .actionCode(ta.getActionCode())
                                        .accessMode("SHARED")
                                        .createdBy(principal.platformUserId())
                                        .updatedBy(principal.platformUserId())
                                        .build());
                            }
                        }
                    }

                    if (userRoleAssignmentRepository != null) {
                        UserRoleAssignment assignment = UserRoleAssignment.builder()
                                .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                                .userAccountId(adminUserAccountId)
                                .roleDefinitionId(roleDef.getId().getRoleDefinitionId())
                                .scopeType(ScopeType.TENANT)
                                .effectiveFrom(java.time.Instant.now())
                                .status(AssignmentStatus.ACTIVE)
                                .createdBy(principal.platformUserId())
                                .updatedBy(principal.platformUserId())
                                .build();
                        userRoleAssignmentRepository.save(assignment);
                    }
                }
            }
        } finally {
            if (entityManager != null) {
                entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
                entityManager.createNativeQuery("RESET app.institution_scope").executeUpdate();
            }
            TenantContext.clear();
        }

        return TenantResponse.from(
                savedTenant,
                plan.getPlanName(),
                request.getFirstAdminEmail(),
                request.getFirstAdminMobile(),
                request.getFirstAdminNationalIdLast4(),
                tempPassword,
                adminUserAccountId
        );
    }

    /**
     * Lists tenants with pagination.
     */
    @Transactional(readOnly = true)
    public TenantPageResponse listTenants(Pageable pageable) {
        Page<Tenant> page = tenantRepository.findAll(pageable);
        List<TenantResponse> items = page.getContent().stream()
                .map(t -> {
                    String planName = planRepository.findById(t.getPlanId())
                            .map(Plan::getPlanName)
                            .orElse("Custom Plan");
                    return TenantResponse.from(t, planName, null, null, null);
                })
                .toList();

        return new TenantPageResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Transactional
    public TenantResponse activateTenant(
            UUID tenantId,
            TenantActivationRequest request,
            PlatformPrincipal principal) {
        return activateTenant(tenantId, request, principal, null, false);
    }

    /** E8-S5: the HTTP path. If-Match must name the tenant's current row version (428 / 409 otherwise). */
    @Transactional
    public TenantResponse activateTenant(UUID tenantId, TenantActivationRequest request, PlatformPrincipal principal,
            String ifMatch) {
        return activateTenant(tenantId, request, principal, ifMatch, true);
    }

    private TenantResponse activateTenant(UUID tenantId, TenantActivationRequest request, PlatformPrincipal principal,
            String ifMatch, boolean checkVersion) {

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
        if (checkVersion) requireTenantVersion(tenant, ifMatch);

        String planName = planRepository.findById(tenant.getPlanId())
                .map(Plan::getPlanName)
                .orElse("Custom Plan");

        if (tenant.getState() == TenantState.ACTIVE) {
            return TenantResponse.from(tenant, planName, null, null, null);
        }

        if (tenant.getState() == TenantState.DELETED || tenant.getState() == TenantState.ARCHIVED
                || tenant.getState() == TenantState.SUSPENDED) {
            throw new BusinessRuleViolationException("INVALID_STATE_TRANSITION",
                    "Cannot activate tenant in state " + tenant.getState() + (tenant.getState() == TenantState.SUSPENDED
                            ? " (use reactivation)" : ""));
        }

        // L-05 / D1: a tenant goes live only after its first invoice is PAID (R2-24).
        if (!invoiceRepository.existsByTenantIdAndStatus(tenantId, "PAID")) {
            throw new BusinessRuleViolationException("PAYMENT_REQUIRED",
                    "Tenant activation refused: no paid invoice exists for this tenant yet.");
        }

        // Statutory go-live blocker evaluation (Rule 02_rules.sql app.go_live_blockers)
        List<String> blockers = new ArrayList<>();
        openTenantScope(tenantId);
        try {

        if ("UNIVERSITY_GROUP".equalsIgnoreCase(tenant.getTenantType())) {
            boolean vcApproverExists = leaveApproverSettingRepository
                    .existsByIdTenantIdAndSubjectRoleAndStatus(tenantId, "VICE_CHANCELLOR", "ACTIVE");
            if (!vcApproverExists) {
                blockers.add("VC_LEAVE_APPROVER_MISSING");
            }
        }

        List<Institution> activeOrDraftInstitutions = institutionRepository
                .findByIdTenantIdAndStatusIn(tenantId, List.of("DRAFT", "ACTIVE"));
        for (Institution inst : activeOrDraftInstitutions) {
            UUID instId = inst.getId().getInstitutionId();
            boolean principalApproverExists = leaveApproverSettingRepository
                    .existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(tenantId, "PRINCIPAL", instId, "ACTIVE");
            if (!principalApproverExists) {
                blockers.add("PRINCIPAL_LEAVE_APPROVER_MISSING for institution " + inst.getInstitutionName() + " (" + instId + ")");
            }
        }
        } finally {
            closeTenantScope();
        }

        if (!blockers.isEmpty()) {
            throw new BusinessRuleViolationException("GO_LIVE_BLOCKED",
                    "Tenant activation refused: statutory go-live blockers exist: " + String.join("; ", blockers));
        }

        tenant.setState(TenantState.ACTIVE);
        tenant.setActivatedAt(Instant.now());
        bumpVersion(tenant);
        tenantRepository.save(tenant);

        platformAuditWriter.record(principal, tenantId, "TENANT_ACTIVATED", "TENANT_ACTIVATE", "TENANT", tenantId);

        return TenantResponse.from(tenant, planName, null, null, null);
    }

    /** Go-live checks read app.* rows, which RLS hides unless the tenant is set (L-05). */
    private void openTenantScope(UUID tenantId) {
        if (entityManager != null) {
            entityManager.createNativeQuery("SET app.tenant_id = '" + tenantId + "'").executeUpdate();
            entityManager.createNativeQuery("SET app.institution_scope = 'ALL'").executeUpdate();
        }
    }

    private void closeTenantScope() {
        if (entityManager != null) {
            entityManager.createNativeQuery("RESET app.tenant_id").executeUpdate();
            entityManager.createNativeQuery("RESET app.institution_scope").executeUpdate();
        }
    }

    private List<Plan> seedDefaultPlans() {
        UUID systemUser = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant now = Instant.now();

        Plan standard = Plan.builder()
                .planId(UUID.fromString("11111111-1111-1111-1111-111111111101"))
                .planName("Standard School Plan")
                .pricingType("PER_STUDENT_YEAR")
                .priceAmount(new BigDecimal("499.00"))
                .currencyCode("INR")
                .userLimit(1000)
                .storageLimitGb(50)
                .smsLimit(5000)
                .apiLimit(10000)
                .maxInstitutions(1)
                .customDomainAllowed(false)
                .brandingColorsAllowed(false)
                .billingCycleMonths(12)
                .status("ACTIVE")
                .createdAt(now)
                .createdBy(systemUser)
                .updatedAt(now)
                .updatedBy(systemUser)
                .rowVersion(1)
                .build();

        Plan premium = Plan.builder()
                .planId(UUID.fromString("11111111-1111-1111-1111-111111111102"))
                .planName("Premium College Plan")
                .pricingType("PER_STUDENT_YEAR")
                .priceAmount(new BigDecimal("999.00"))
                .currencyCode("INR")
                .userLimit(5000)
                .storageLimitGb(250)
                .smsLimit(25000)
                .apiLimit(50000)
                .maxInstitutions(5)
                .customDomainAllowed(false)
                .brandingColorsAllowed(false)
                .billingCycleMonths(12)
                .status("ACTIVE")
                .createdAt(now)
                .createdBy(systemUser)
                .updatedAt(now)
                .updatedBy(systemUser)
                .rowVersion(1)
                .build();

        Plan enterprise = Plan.builder()
                .planId(UUID.fromString("11111111-1111-1111-1111-111111111103"))
                .planName("Enterprise University Group Plan")
                .pricingType("PER_STUDENT_YEAR")
                .priceAmount(new BigDecimal("1999.00"))
                .currencyCode("INR")
                .userLimit(25000)
                .storageLimitGb(1000)
                .smsLimit(100000)
                .apiLimit(200000)
                .maxInstitutions(25)
                .customDomainAllowed(false)
                .brandingColorsAllowed(false)
                .billingCycleMonths(12)
                .status("ACTIVE")
                .createdAt(now)
                .createdBy(systemUser)
                .updatedAt(now)
                .updatedBy(systemUser)
                .rowVersion(1)
                .build();

        return planRepository.saveAll(List.of(standard, premium, enterprise));
    }

    @Transactional
    public PlanResponse createPlan(CreatePlanRequest request, PlatformPrincipal principal) {
        if (principal.platformRole() != PlatformRole.PRODUCT_OWNER) {
            if ((request.priceAmount() != null && request.priceAmount().compareTo(BigDecimal.ZERO) != 0)
                    || (request.pricingType() != null && !"PER_STUDENT_YEAR".equals(request.pricingType()))
                    || (request.currencyCode() != null && !"INR".equalsIgnoreCase(request.currencyCode()))
                    || (request.smsUnitAmount() != null && request.smsUnitAmount().compareTo(BigDecimal.ZERO) != 0)
                    || (request.storageUnitAmountPerGb() != null && request.storageUnitAmountPerGb().compareTo(BigDecimal.ZERO) != 0)) {
                throw new UnauthorizedException("Rule [F-4]: only the Product Owner can configure plan pricing.");
            }
        }

        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        Plan plan = Plan.builder()
                .planId(planId)
                .planName(request.planName().trim())
                .pricingType(request.pricingType() != null ? request.pricingType() : "PER_STUDENT_YEAR")
                .priceAmount(request.priceAmount() != null ? request.priceAmount() : BigDecimal.ZERO)
                .currencyCode(request.currencyCode() != null ? request.currencyCode().toUpperCase() : "INR")
                .userLimit(request.userLimit() != null ? request.userLimit() : 1000)
                .storageLimitGb(request.storageLimitGb() != null ? request.storageLimitGb() : 50)
                .smsLimit(request.smsLimit() != null ? request.smsLimit() : 5000)
                .apiLimit(request.apiLimit() != null ? request.apiLimit() : 10000)
                .maxInstitutions(request.maxInstitutions() != null ? request.maxInstitutions() : 1)
                .customDomainAllowed(Boolean.TRUE.equals(request.customDomainAllowed()))
                .brandingColorsAllowed(Boolean.TRUE.equals(request.brandingColorsAllowed()))
                .billingCycleMonths(request.billingCycleMonths() != null ? request.billingCycleMonths() : 12)
                .smsUnitAmount(principal.platformRole() == PlatformRole.PRODUCT_OWNER ? request.smsUnitAmount() : null)
                .storageUnitAmountPerGb(principal.platformRole() == PlatformRole.PRODUCT_OWNER ? request.storageUnitAmountPerGb() : null)
                .status("ACTIVE")
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        Plan saved = planRepository.save(plan);
        platformAuditWriter.record(principal, "CREATE", "PLAN_MANAGE", "PLAN", saved.getPlanId());

        return PlanResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlan(UUID planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan " + planId + " was not found."));
        return PlanResponse.from(plan);
    }

    @Transactional
    public PlanResponse updatePlan(UUID planId, UpdatePlanRequest request, PlatformPrincipal principal, String ifMatch) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan " + planId + " was not found."));

        new PlatformConcurrency().requireIfMatch(ifMatch, plan.getRowVersion() == null ? 0 : plan.getRowVersion());

        boolean priceChangeAttempted = (request.priceAmount() != null
                && (plan.getPriceAmount() == null || request.priceAmount().compareTo(plan.getPriceAmount()) != 0))
                || (request.currencyCode() != null && !request.currencyCode().equalsIgnoreCase(plan.getCurrencyCode()))
                || (request.pricingType() != null && !request.pricingType().equals(plan.getPricingType()))
                || (request.smsUnitAmount() != null && (plan.getSmsUnitAmount() == null
                        || request.smsUnitAmount().compareTo(plan.getSmsUnitAmount()) != 0))
                || (request.storageUnitAmountPerGb() != null && (plan.getStorageUnitAmountPerGb() == null
                        || request.storageUnitAmountPerGb().compareTo(plan.getStorageUnitAmountPerGb()) != 0));
        if (principal.platformRole() != PlatformRole.PRODUCT_OWNER && priceChangeAttempted) {
            platformAuditWriter.recordDenied(principal, "PLAN_MANAGE", "PLAN", planId);
            throw new ApiException(HttpStatus.FORBIDDEN, "PRICE_CHANGE_PO_ONLY",
                    "Only the Product Owner can change plan pricing.");
        }

        Map<String, Map<String, Object>> auditChanges = new LinkedHashMap<>();
        if (request.planName() != null && !request.planName().isBlank()) {
            String next = request.planName().trim();
            addAuditChange(auditChanges, "planName", plan.getPlanName(), next);
            plan.setPlanName(next);
        }
        if (request.priceAmount() != null && principal.platformRole() == PlatformRole.PRODUCT_OWNER) {
            addAuditChange(auditChanges, "priceAmount", plan.getPriceAmount(), request.priceAmount());
            plan.setPriceAmount(request.priceAmount());
        }
        if (request.pricingType() != null && principal.platformRole() == PlatformRole.PRODUCT_OWNER) {
            addAuditChange(auditChanges, "pricingType", plan.getPricingType(), request.pricingType());
            plan.setPricingType(request.pricingType());
        }
        if (request.currencyCode() != null && principal.platformRole() == PlatformRole.PRODUCT_OWNER && !request.currencyCode().isBlank()) {
            String next = request.currencyCode().toUpperCase();
            addAuditChange(auditChanges, "currencyCode", plan.getCurrencyCode(), next);
            plan.setCurrencyCode(next);
        }
        if (request.userLimit() != null) {
            addAuditChange(auditChanges, "userLimit", plan.getUserLimit(), request.userLimit());
            plan.setUserLimit(request.userLimit());
        }
        if (request.storageLimitGb() != null) {
            addAuditChange(auditChanges, "storageLimitGb", plan.getStorageLimitGb(), request.storageLimitGb());
            plan.setStorageLimitGb(request.storageLimitGb());
        }
        if (request.smsLimit() != null) {
            addAuditChange(auditChanges, "smsLimit", plan.getSmsLimit(), request.smsLimit());
            plan.setSmsLimit(request.smsLimit());
        }
        if (request.apiLimit() != null) {
            addAuditChange(auditChanges, "apiLimit", plan.getApiLimit(), request.apiLimit());
            plan.setApiLimit(request.apiLimit());
        }
        if (request.maxInstitutions() != null) {
            addAuditChange(auditChanges, "maxInstitutions", plan.getMaxInstitutions(), request.maxInstitutions());
            plan.setMaxInstitutions(request.maxInstitutions());
        }
        if (request.customDomainAllowed() != null) {
            addAuditChange(auditChanges, "customDomainAllowed", plan.isCustomDomainAllowed(), request.customDomainAllowed());
            plan.setCustomDomainAllowed(request.customDomainAllowed());
        }
        if (request.brandingColorsAllowed() != null) {
            addAuditChange(auditChanges, "brandingColorsAllowed", plan.isBrandingColorsAllowed(), request.brandingColorsAllowed());
            plan.setBrandingColorsAllowed(request.brandingColorsAllowed());
        }
        if (request.billingCycleMonths() != null) {
            addAuditChange(auditChanges, "billingCycleMonths", plan.getBillingCycleMonths(), request.billingCycleMonths());
            plan.setBillingCycleMonths(request.billingCycleMonths());
        }
        if (request.smsUnitAmount() != null && principal.platformRole() == PlatformRole.PRODUCT_OWNER) {
            addAuditChange(auditChanges, "smsUnitAmount", plan.getSmsUnitAmount(), request.smsUnitAmount());
            plan.setSmsUnitAmount(request.smsUnitAmount());
        }
        if (request.storageUnitAmountPerGb() != null && principal.platformRole() == PlatformRole.PRODUCT_OWNER) {
            addAuditChange(auditChanges, "storageUnitAmountPerGb", plan.getStorageUnitAmountPerGb(), request.storageUnitAmountPerGb());
            plan.setStorageUnitAmountPerGb(request.storageUnitAmountPerGb());
        }
        if (request.status() != null && !request.status().isBlank()) {
            String next = request.status().toUpperCase();
            addAuditChange(auditChanges, "status", plan.getStatus(), next);
            plan.setStatus(next);
        }

        plan.setUpdatedAt(Instant.now());
        plan.setUpdatedBy(principal.platformUserId());
        int currentVersion = plan.getRowVersion() != null ? plan.getRowVersion() : 0;
        plan.setRowVersion(currentVersion + 1);

        Plan saved = planRepository.save(plan);
        platformAuditWriter.record(principal, null, "PLAN_UPDATED", "UPDATE", "PLAN_MANAGE", "PLAN", saved.getPlanId(),
                PlatformAuditWriter.SUCCESS, auditChanges);

        return PlanResponse.from(saved);
    }

    private static void addAuditChange(Map<String, Map<String, Object>> changes, String field, Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("old", oldValue);
        pair.put("new", newValue);
        changes.put(field, pair);
    }

    @Transactional
    public TenantResponse suspendTenant(UUID tenantId, String reason, PlatformPrincipal principal) {
        return suspendTenant(tenantId, reason, principal, null, false);
    }

    /** E8-S5: the HTTP path. If-Match must name the tenant's current row version (428 / 409 otherwise). */
    @Transactional
    public TenantResponse suspendTenant(UUID tenantId, String reason, PlatformPrincipal principal, String ifMatch) {
        return suspendTenant(tenantId, reason, principal, ifMatch, true);
    }

    private TenantResponse suspendTenant(UUID tenantId, String reason, PlatformPrincipal principal, String ifMatch,
            boolean checkVersion) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));
        if (checkVersion) requireTenantVersion(tenant, ifMatch);
        requireTransition(tenant, TenantState.SUSPENDED);

        Instant now = Instant.now();
        tenant.setState(TenantState.SUSPENDED);
        tenant.setSuspendedAt(now);
        tenant.setArchivedAt(null);
        tenant.setUpdatedAt(now);
        tenant.setUpdatedBy(principal.platformUserId());
        bumpVersion(tenant);
        tenantRepository.save(tenant);

        if (tenantIssuerResolver != null && tenant.getKeycloakRealm() != null) {
            tenantIssuerResolver.evictTenantIssuer(tenant.getKeycloakRealm());
        }

        platformAuditWriter.record(principal, tenantId, "TENANT_SUSPENDED", "TENANT_SUSPEND_ARCHIVE", "TENANT", tenantId);

        String planName = getPlanName(tenant.getPlanId());
        return TenantResponse.from(tenant, planName, null, null, null);
    }

    @Transactional
    public TenantResponse archiveTenant(UUID tenantId, String reason, PlatformPrincipal principal) {
        return archiveTenant(tenantId, reason, principal, null, false);
    }

    /** E8-S5: the HTTP path. If-Match must name the tenant's current row version (428 / 409 otherwise). */
    @Transactional
    public TenantResponse archiveTenant(UUID tenantId, String reason, PlatformPrincipal principal, String ifMatch) {
        return archiveTenant(tenantId, reason, principal, ifMatch, true);
    }

    private TenantResponse archiveTenant(UUID tenantId, String reason, PlatformPrincipal principal, String ifMatch,
            boolean checkVersion) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));
        if (checkVersion) requireTenantVersion(tenant, ifMatch);
        requireTransition(tenant, TenantState.ARCHIVED);

        Instant now = Instant.now();
        tenant.setState(TenantState.ARCHIVED);
        tenant.setArchivedAt(now);
        tenant.setUpdatedAt(now);
        tenant.setUpdatedBy(principal.platformUserId());
        bumpVersion(tenant);
        tenantRepository.save(tenant);

        if (tenantIssuerResolver != null && tenant.getKeycloakRealm() != null) {
            tenantIssuerResolver.evictTenantIssuer(tenant.getKeycloakRealm());
        }

        platformAuditWriter.record(principal, tenantId, "TENANT_ARCHIVED", "TENANT_SUSPEND_ARCHIVE", "TENANT", tenantId);

        String planName = getPlanName(tenant.getPlanId());
        return TenantResponse.from(tenant, planName, null, null, null);
    }

    @Transactional
    public TenantResponse reactivateTenant(UUID tenantId, PlatformPrincipal principal) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));
        requireTransition(tenant, TenantState.ACTIVE);

        Instant now = Instant.now();
        tenant.setState(TenantState.ACTIVE);
        tenant.setSuspendedAt(null);
        tenant.setArchivedAt(null);
        tenant.setUpdatedAt(now);
        tenant.setUpdatedBy(principal.platformUserId());
        tenantRepository.save(tenant);

        if (tenantIssuerResolver != null && tenant.getKeycloakRealm() != null) {
            tenantIssuerResolver.evictTenantIssuer(tenant.getKeycloakRealm());
        }

        platformAuditWriter.record(principal, tenantId, "TENANT_REACTIVATED", "TENANT_SUSPEND_ARCHIVE", "TENANT", tenantId);

        String planName = getPlanName(tenant.getPlanId());
        return TenantResponse.from(tenant, planName, null, null, null);
    }

    /** L-09: lifecycle moves only along allowed edges; a DELETED (locked) tenant never comes back. */
    /** E8-S5: If-Match must name the tenant's current row_version (428 IF_MATCH_REQUIRED / 409 VERSION_CONFLICT). */
    private static void requireTenantVersion(Tenant tenant, String ifMatch) {
        new PlatformConcurrency().requireIfMatch(ifMatch, tenant.getRowVersion() == null ? 0 : tenant.getRowVersion());
    }

    /**
     * Keeps the in-memory row_version equal to what platform.fn_touch writes (OLD + 1) so the response carries the
     * version the client must send next time.
     */
    private static void bumpVersion(Tenant tenant) {
        tenant.setRowVersion(tenant.getRowVersion() == null ? 1 : tenant.getRowVersion() + 1);
    }

    static void requireTransition(Tenant tenant, TenantState target) {
        TenantState from = tenant.getState();
        boolean allowed = switch (target) {
            case SUSPENDED -> from == TenantState.ACTIVE || from == TenantState.TRIAL;
            case ARCHIVED -> from == TenantState.ACTIVE || from == TenantState.TRIAL || from == TenantState.SUSPENDED;
            case ACTIVE -> from == TenantState.SUSPENDED || from == TenantState.ARCHIVED;
            default -> false;
        };
        if (!allowed) {
            throw new BusinessRuleViolationException("INVALID_STATE_TRANSITION",
                    "A tenant in state " + from + " cannot be moved to " + target + ".");
        }
    }

    public TenantSettingsResponse getTenantSettings(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));

        Plan plan = tenant.getPlanId() == null ? null : planRepository.findById(tenant.getPlanId()).orElse(null);
        return TenantSettingsResponse.from(tenant, plan == null ? "Custom Plan" : plan.getPlanName(), plan);
    }

    @Transactional
    public TenantSettingsResponse updateTenantSettings(UUID tenantId, UpdateTenantSettingsRequest request, PlatformPrincipal principal) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));
        Plan plan = tenant.getPlanId() == null ? null : planRepository.findById(tenant.getPlanId()).orElse(null);

        // E1-S3 (WF-06, R2-14): a limit above the plan is the Product Owner's decision only.
        if (plan != null && principal.platformRole() != PlatformRole.PRODUCT_OWNER) {
            List<String> beyond = new java.util.ArrayList<>();
            if (exceeds(request.userLimit(), plan.getUserLimit(), tenant.getUserLimit())) beyond.add("userLimit");
            if (exceeds(request.storageLimitGb(), plan.getStorageLimitGb(), tenant.getStorageLimitGb())) beyond.add("storageLimitGb");
            if (exceeds(request.smsLimit(), plan.getSmsLimit(), tenant.getSmsLimit())) beyond.add("smsLimit");
            if (exceeds(request.apiLimit(), plan.getApiLimit(), tenant.getApiLimit())) beyond.add("apiLimit");
            if (!beyond.isEmpty()) {
                platformAuditWriter.record(principal, tenantId, "TENANT_SETTINGS_UPDATED", "UPDATE",
                        "TENANT_SETTINGS", "TENANT", tenantId, "DENIED",
                        Map.of("beyondPlan", Map.of("new", String.join(",", beyond))));
                throw new ApiException(HttpStatus.FORBIDDEN, "BEYOND_PLAN_PO_ONLY",
                        "Beyond the plan - only the Product Owner can set " + String.join(", ", beyond)
                                + " above the tenant's plan.");
            }
        }
        // A custom domain needs a plan that includes it - for everyone, the Product Owner too.
        String requestedDomain = request.customDomain() == null ? null : request.customDomain().trim().toLowerCase();
        if (requestedDomain != null && !requestedDomain.isEmpty()
                && !requestedDomain.equals(tenant.getCustomDomain())
                && (plan == null || !plan.isCustomDomainAllowed())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CUSTOM_DOMAIN_NOT_IN_PLAN",
                    "This tenant's plan does not include a custom domain. Change the plan first.");
        }
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        trackChange(changes, "userLimit", tenant.getUserLimit(), request.userLimit());
        trackChange(changes, "storageLimitGb", tenant.getStorageLimitGb(), request.storageLimitGb());
        trackChange(changes, "smsLimit", tenant.getSmsLimit(), request.smsLimit());
        trackChange(changes, "apiLimit", tenant.getApiLimit(), request.apiLimit());
        trackChange(changes, "customDomain", tenant.getCustomDomain(), requestedDomain);
        trackChange(changes, "analyticsOptOut", tenant.isAnalyticsOptOut(), request.analyticsOptOut());

        if (request.timeZone() != null && !request.timeZone().isBlank()) {
            tenant.setTimeZone(request.timeZone().trim());
        }
        if (request.dataRegion() != null && !request.dataRegion().isBlank()) {
            tenant.setDataRegion(request.dataRegion().trim().toUpperCase());
        }
        if (request.analyticsOptOut() != null) {
            tenant.setAnalyticsOptOut(request.analyticsOptOut());
        }
        if (request.customDomain() != null) {
            tenant.setCustomDomain(request.customDomain().trim().toLowerCase());
        }
        if (request.userLimit() != null) {
            tenant.setUserLimit(request.userLimit());
        }
        if (request.storageLimitGb() != null) {
            tenant.setStorageLimitGb(request.storageLimitGb());
        }
        if (request.smsLimit() != null) {
            tenant.setSmsLimit(request.smsLimit());
        }
        if (request.apiLimit() != null) {
            tenant.setApiLimit(request.apiLimit());
        }

        tenant.setUpdatedAt(Instant.now());
        tenant.setUpdatedBy(principal.platformUserId());
        Tenant saved = tenantRepository.save(tenant);

        platformAuditWriter.record(principal, tenantId, "TENANT_SETTINGS_UPDATED", "UPDATE", "TENANT_SETTINGS",
                "TENANT", tenantId, PlatformAuditWriter.SUCCESS, changes);

        return TenantSettingsResponse.from(saved, plan == null ? "Custom Plan" : plan.getPlanName(), plan);
    }

    /** True when a new value is above the plan and actually raises the tenant's current value. */
    private static boolean exceeds(Integer requested, Integer planLimit, Integer current) {
        return requested != null && planLimit != null && requested > planLimit
                && (current == null || requested > current || current <= planLimit);
    }

    private static void trackChange(Map<String, Map<String, Object>> changes, String field, Object oldValue, Object newValue) {
        if (newValue != null && !java.util.Objects.equals(oldValue, newValue)) {
            Map<String, Object> change = new java.util.HashMap<>();
            change.put("old", oldValue);
            change.put("new", newValue);
            changes.put(field, change);
        }
    }

    private String getPlanName(UUID planId) {
        if (planId == null) return "Custom Plan";
        return planRepository.findById(planId).map(Plan::getPlanName).orElse("Custom Plan");
    }

}
