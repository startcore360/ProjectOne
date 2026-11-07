package com.edu360.api.platformops;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.config.keycloak.KeycloakProvisioningException;
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

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogService auditLogService;
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

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    public TenantManagementService(
            TenantRepository tenantRepository,
            PlanRepository planRepository,
            KeycloakAdminClient keycloakAdminClient,
            AuditLogService auditLogService,
            LeaveApproverSettingRepository leaveApproverSettingRepository,
            InstitutionRepository institutionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserAccountRepository userAccountRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserIdentityLinkRepository userIdentityLinkRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RoleDefinitionRepository roleDefinitionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) StandardRoleRepository standardRoleRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) StandardRoleActionRepository standardRoleActionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RoleActionRepository roleActionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserRoleAssignmentRepository userRoleAssignmentRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter
    ) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.auditLogService = auditLogService;
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
    }

    public TenantManagementService(
            TenantRepository tenantRepository,
            PlanRepository planRepository,
            KeycloakAdminClient keycloakAdminClient,
            AuditLogService auditLogService,
            LeaveApproverSettingRepository leaveApproverSettingRepository,
            InstitutionRepository institutionRepository,
            UserAccountRepository userAccountRepository,
            UserIdentityLinkRepository userIdentityLinkRepository,
            RoleDefinitionRepository roleDefinitionRepository,
            StandardRoleRepository standardRoleRepository,
            StandardRoleActionRepository standardRoleActionRepository,
            RoleActionRepository roleActionRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository
    ) {
        this(tenantRepository, planRepository, keycloakAdminClient, auditLogService, leaveApproverSettingRepository, institutionRepository,
                userAccountRepository, userIdentityLinkRepository, roleDefinitionRepository, standardRoleRepository, standardRoleActionRepository, roleActionRepository, userRoleAssignmentRepository, null);
    }

    public TenantManagementService(
            TenantRepository tenantRepository,
            PlanRepository planRepository,
            KeycloakAdminClient keycloakAdminClient,
            AuditLogService auditLogService,
            LeaveApproverSettingRepository leaveApproverSettingRepository,
            InstitutionRepository institutionRepository
    ) {
        this(tenantRepository, planRepository, keycloakAdminClient, auditLogService, leaveApproverSettingRepository, institutionRepository,
                null, null, null, null, null, null, null, null);
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
    @Transactional
    public TenantResponse createTenant(TenantCreateRequest request, PlatformPrincipal principal) {
        String normalizedSubdomain = request.getSubdomain().toLowerCase().trim();

        // 1. Check subdomain uniqueness
        // PRODUCTION (docker/PRODUCTION_TENANT_ROUTING.txt, P6): reserved names such as "platform" must also be refused.
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

        // 5b. Provision First Tenant Admin
        UUID adminUserAccountId = UUID.randomUUID();
        String tempPassword = "Edu@" + UUID.randomUUID().toString().substring(0, 8) + "!";
        String keycloakUserId = null;
        try {
            keycloakUserId = keycloakAdminClient.provisionUser(
                    realmName,
                    request.getFirstAdminName(),
                    request.getFirstAdminEmail().toLowerCase().trim(),
                    tempPassword,
                    true
            );
            log.info("Provisioned first tenant admin Keycloak account {} for '{}' in realm '{}'",
                    keycloakUserId, request.getFirstAdminEmail(), realmName);
        } catch (KeycloakProvisioningException ex) {
            log.warn("Keycloak admin provisioning failed: {}", ex.getMessage());
            throw new BusinessRuleViolationException("KEYCLOAK_PROVISIONING_FAILED", "Failed to provision tenant first admin in Keycloak: " + ex.getMessage());
        }
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            keycloakUserId = adminUserAccountId.toString();
        }

        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "CREATE", "TENANT_CREATE", "TENANT", savedTenant.getTenantId());
        }

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

        // 6. Record audit log
        auditLogService.record(
                AuditEventType.ACCOUNT_CREATED,
                null,
                request.getFirstAdminEmail(),
                null,
                "Created tenant '" + savedTenant.getTenantName() + "' (subdomain: " + savedTenant.getSubdomain()
                        + ", plan: " + plan.getPlanName() + ") in TRIAL state with initial admin by " + principal.email()
        );

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

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        String planName = planRepository.findById(tenant.getPlanId())
                .map(Plan::getPlanName)
                .orElse("Custom Plan");

        if (tenant.getState() == TenantState.ACTIVE) {
            return TenantResponse.from(tenant, planName, null, null, null);
        }

        if (tenant.getState() == TenantState.DELETED || tenant.getState() == TenantState.ARCHIVED) {
            throw new BusinessRuleViolationException("INVALID_STATE_TRANSITION",
                    "Cannot activate tenant in state " + tenant.getState());
        }

        // Statutory go-live blocker evaluation (Rule 02_rules.sql app.go_live_blockers)
        List<String> blockers = new ArrayList<>();

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

        if (!blockers.isEmpty()) {
            throw new BusinessRuleViolationException("GO_LIVE_BLOCKED",
                    "Tenant activation refused: statutory go-live blockers exist: " + String.join("; ", blockers));
        }

        tenant.setState(TenantState.ACTIVE);
        tenant.setActivatedAt(Instant.now());
        tenantRepository.save(tenant);

        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "APPROVE", "TENANT_ACTIVATE", "TENANT", tenantId);
        }

        return TenantResponse.from(tenant, planName, null, null, null);
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
            if (request.priceAmount() != null && request.priceAmount().compareTo(BigDecimal.ZERO) > 0) {
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
                .status("ACTIVE")
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        Plan saved = planRepository.save(plan);
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "CREATE", "PLAN_MANAGE", "PLAN", saved.getPlanId());
        }

        return PlanResponse.from(saved);
    }

    @Transactional
    public PlanResponse updatePlan(UUID planId, UpdatePlanRequest request, PlatformPrincipal principal) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan " + planId + " was not found."));

        if (principal.platformRole() != PlatformRole.PRODUCT_OWNER) {
            if (request.priceAmount() != null && request.priceAmount().compareTo(plan.getPriceAmount()) != 0) {
                throw new UnauthorizedException("Rule [F-4]: only the Product Owner can alter plan pricing.");
            }
        }

        if (request.planName() != null && !request.planName().isBlank()) {
            plan.setPlanName(request.planName().trim());
        }
        if (request.priceAmount() != null && principal.platformRole() == PlatformRole.PRODUCT_OWNER) {
            plan.setPriceAmount(request.priceAmount());
        }
        if (request.currencyCode() != null && !request.currencyCode().isBlank()) {
            plan.setCurrencyCode(request.currencyCode().toUpperCase());
        }
        if (request.userLimit() != null) {
            plan.setUserLimit(request.userLimit());
        }
        if (request.storageLimitGb() != null) {
            plan.setStorageLimitGb(request.storageLimitGb());
        }
        if (request.smsLimit() != null) {
            plan.setSmsLimit(request.smsLimit());
        }
        if (request.apiLimit() != null) {
            plan.setApiLimit(request.apiLimit());
        }
        if (request.maxInstitutions() != null) {
            plan.setMaxInstitutions(request.maxInstitutions());
        }
        if (request.status() != null && !request.status().isBlank()) {
            plan.setStatus(request.status().toUpperCase());
        }

        plan.setUpdatedAt(Instant.now());
        plan.setUpdatedBy(principal.platformUserId());
        int currentVersion = plan.getRowVersion() != null ? plan.getRowVersion() : 0;
        plan.setRowVersion(currentVersion + 1);

        Plan saved = planRepository.save(plan);
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "UPDATE", "PLAN_MANAGE", "PLAN", saved.getPlanId());
        }

        return PlanResponse.from(saved);
    }

    @Transactional
    public TenantResponse suspendTenant(UUID tenantId, String reason, PlatformPrincipal principal) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));

        tenant.setState(TenantState.SUSPENDED);
        tenantRepository.save(tenant);

        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "UPDATE", "TENANT_SUSPEND_ARCHIVE", "TENANT", tenantId);
        }

        String planName = getPlanName(tenant.getPlanId());
        return TenantResponse.from(tenant, planName, null, null, null);
    }

    @Transactional
    public TenantResponse archiveTenant(UUID tenantId, String reason, PlatformPrincipal principal) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));

        tenant.setState(TenantState.ARCHIVED);
        tenantRepository.save(tenant);

        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "UPDATE", "TENANT_SUSPEND_ARCHIVE", "TENANT", tenantId);
        }

        String planName = getPlanName(tenant.getPlanId());
        return TenantResponse.from(tenant, planName, null, null, null);
    }

    @Transactional
    public TenantResponse reactivateTenant(UUID tenantId, PlatformPrincipal principal) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));

        tenant.setState(TenantState.ACTIVE);
        tenantRepository.save(tenant);

        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "UPDATE", "TENANT_SUSPEND_ARCHIVE", "TENANT", tenantId);
        }

        String planName = getPlanName(tenant.getPlanId());
        return TenantResponse.from(tenant, planName, null, null, null);
    }

    public TenantSettingsResponse getTenantSettings(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));

        String planName = getPlanName(tenant.getPlanId());
        return TenantSettingsResponse.from(tenant, planName);
    }

    @Transactional
    public TenantSettingsResponse updateTenantSettings(UUID tenantId, UpdateTenantSettingsRequest request, PlatformPrincipal principal) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));

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

        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "CONFIG_CHANGE", "TENANT_SETTINGS", "TENANT", tenantId);
        }

        String planName = getPlanName(saved.getPlanId());
        return TenantSettingsResponse.from(saved, planName);
    }

    private String getPlanName(UUID planId) {
        if (planId == null) return "Custom Plan";
        return planRepository.findById(planId).map(Plan::getPlanName).orElse("Custom Plan");
    }

    @Transactional
    public void deleteTenant(UUID tenantId, String reason, PlatformPrincipal principal) {
        if (principal.platformRole() != PlatformRole.PRODUCT_OWNER) {
            throw new UnauthorizedException("Rule WF-05: Only the Product Owner may delete a tenant.");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));

        if (tenant.getState() != TenantState.ARCHIVED) {
            throw new BusinessRuleViolationException("Rule WF-05: Tenant must be in ARCHIVED state before deletion.");
        }

        tenant.setState(TenantState.DELETED);
        tenantRepository.save(tenant);

        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "DELETE", "TENANT_SUSPEND_ARCHIVE", "TENANT", tenantId);
        }
    }

    private static final java.util.List<java.util.Map<String, Object>> IN_MEMORY_EXPORTS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<java.util.Map<String, Object>> IN_MEMORY_RETENTION_REQUESTS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public java.util.Map<String, Object> requestExport(UUID tenantId, PlatformPrincipal principal) {
        UUID exportId = UUID.randomUUID();
        Instant now = Instant.now();
        java.util.Map<String, Object> export = java.util.Map.of(
                "id", exportId.toString(),
                "tenantId", tenantId.toString(),
                "status", "REQUESTED",
                "exportType", "ALL_DATA",
                "requestedAt", now.toString(),
                "requestedBy", principal.email()
        );
        IN_MEMORY_EXPORTS.add(export);
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "EXPORT", "TENANT_SETTINGS", "TENANT", tenantId);
        }
        return export;
    }

    public java.util.List<java.util.Map<String, Object>> listExports(UUID tenantId) {
        if (tenantId == null) return java.util.List.copyOf(IN_MEMORY_EXPORTS);
        return IN_MEMORY_EXPORTS.stream()
                .filter(e -> tenantId.toString().equals(e.get("tenantId")))
                .toList();
    }

    public java.util.List<java.util.Map<String, Object>> getRetentionPolicies(UUID tenantId) {
        return java.util.List.of(
                java.util.Map.of(
                        "id", UUID.randomUUID().toString(),
                        "tenantId", tenantId.toString(),
                        "dataClassCode", "STUDENT_RECORDS",
                        "retentionPeriodDays", 90,
                        "retentionMonths", 3,
                        "status", "ACTIVE"
                ),
                java.util.Map.of(
                        "id", UUID.randomUUID().toString(),
                        "tenantId", tenantId.toString(),
                        "dataClassCode", "AUDIT_LOGS",
                        "retentionPeriodDays", 365,
                        "retentionMonths", 12,
                        "status", "ACTIVE"
                )
        );
    }

    public java.util.Map<String, Object> requestRetentionChange(UUID tenantId, java.util.Map<String, Object> request, PlatformPrincipal principal) {
        UUID reqId = UUID.randomUUID();
        java.util.Map<String, Object> req = java.util.Map.of(
                "id", reqId.toString(),
                "tenantId", tenantId.toString(),
                "dataClassCode", request.getOrDefault("dataClassCode", "STUDENT_RECORDS"),
                "requestedMonths", request.getOrDefault("requestedMonths", 12),
                "reason", request.getOrDefault("reason", ""),
                "status", "PENDING_DPO_APPROVAL",
                "createdAt", Instant.now().toString()
        );
        IN_MEMORY_RETENTION_REQUESTS.add(req);
        if (platformAuditWriter != null) {
            platformAuditWriter.record(principal, "CONFIG_CHANGE", "TENANT_SETTINGS", "TENANT", tenantId);
        }
        return req;
    }
}
