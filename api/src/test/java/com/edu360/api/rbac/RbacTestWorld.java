package com.edu360.api.rbac;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.edu360.api.audit.AuditLog;
import com.edu360.api.audit.AuditLogRepository;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.identity.IdentityLinkStatus;
import com.edu360.api.identity.TenantUserLookup;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserIdentityLink;
import com.edu360.api.identity.UserIdentityLinkId;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.identity.UserType;
import com.edu360.api.identity.UsernameKind;
import com.edu360.api.selfservice.UserActiveRole;
import com.edu360.api.selfservice.UserActiveRoleRepository;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantState;

/**
 * Test fixture for B1 (Authorisation core): mocked repositories that return REAL role and
 * action rows, read from the same reference data the database is seeded with
 * ({@code sql/05_ref_data.sql}: ref.action_catalog, ref.standard_role, ref.standard_role_action).
 * The {@link PermissionEvaluatorService} built here is the real one (rule R3); only the storage
 * underneath it is in memory. A tenant role is a copy of a standard role with exactly the actions
 * D3 gives that role, as RoleAssignmentService copies them at runtime.
 *
 * {@link #signIn(UUID)} runs the real TenantUserLookup, so TenantContext (active role, scope,
 * "choice required") is set exactly as a real request would set it, and audit rows written by the
 * real AuditLogService are captured in {@link #auditRows}.
 */
public final class RbacTestWorld {

    // ----------------------------------------------------------------------- reference data

    public record ActionRow(String code, boolean approval, String sensitiveClass) {
    }

    public record StandardRoleRow(String code, String name, RoleLevel level) {
    }

    public static final Map<String, ActionRow> ACTIONS = new LinkedHashMap<>();
    public static final Map<String, StandardRoleRow> STANDARD_ROLES = new LinkedHashMap<>();
    public static final Map<String, List<String>> STANDARD_ROLE_ACTIONS = new HashMap<>();

    static {
        try {
            String sql = Files.readString(Path.of("..", "sql", "05_ref_data.sql"))
                    + (Files.exists(Path.of("..", "sql", "10d_v40_rolldown_ref.sql"))
                            ? "\n" + Files.readString(Path.of("..", "sql", "10d_v40_rolldown_ref.sql")) : "");
            Matcher m = Pattern.compile("INSERT INTO ref\\.(action_catalog|standard_role|standard_role_action) VALUES \\((.*)\\) ON CONFLICT")
                    .matcher(sql);
            while (m.find()) {
                List<String> v = tuple(m.group(2));
                switch (m.group(1)) {
                    case "action_catalog" -> ACTIONS.put(v.get(0),
                            new ActionRow(v.get(0), Boolean.parseBoolean(v.get(5)), "NULL".equals(v.get(6)) ? null : v.get(6)));
                    case "standard_role" -> STANDARD_ROLES.put(v.get(0),
                            new StandardRoleRow(v.get(0), v.get(1), RoleLevel.valueOf(v.get(2))));
                    case "standard_role_action" -> STANDARD_ROLE_ACTIONS
                            .computeIfAbsent(v.get(1), k -> new ArrayList<>()).add(v.get(2));
                    default -> { }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read sql/05_ref_data.sql", e);
        }
    }

    /** Splits one SQL VALUES tuple into its values, honouring quotes and '' escapes. */
    private static List<String> tuple(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted) {
                if (c == '\'' && i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                    cur.append('\'');
                    i++;
                } else if (c == '\'') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '\'') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    /** True when D3 gives the standard role this action (ref.standard_role_action). */
    public static boolean standardRoleHolds(String roleCode, String actionCode) {
        return STANDARD_ROLE_ACTIONS.getOrDefault(roleCode, List.of()).contains(actionCode);
    }

    // ----------------------------------------------------------------------- the tenant

    public final UUID tenantId = UUID.randomUUID();
    public final Tenant tenant;

    public final ActionCatalogRepository actionCatalogRepository = mock(ActionCatalogRepository.class);
    public final UserRoleAssignmentRepository userRoleAssignmentRepository = mock(UserRoleAssignmentRepository.class);
    public final RoleActionRepository roleActionRepository = mock(RoleActionRepository.class);
    public final RoleDefinitionRepository roleDefinitionRepository = mock(RoleDefinitionRepository.class);
    public final UserActiveRoleRepository userActiveRoleRepository = mock(UserActiveRoleRepository.class);
    public final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    public final UserIdentityLinkRepository userIdentityLinkRepository = mock(UserIdentityLinkRepository.class);
    public final StandardRoleRepository standardRoleRepository = mock(StandardRoleRepository.class);
    public final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);

    public final Map<UUID, RoleDefinition> roles = new LinkedHashMap<>();
    public final List<RoleAction> roleActions = new ArrayList<>();
    public final List<UserRoleAssignment> assignments = new ArrayList<>();
    public final Map<UUID, UserActiveRole> activeChoices = new HashMap<>();
    public final Map<UUID, UserAccount> accounts = new LinkedHashMap<>();
    public final List<AuditLog> auditRows = new ArrayList<>();

    public final PermissionEvaluatorService evaluator;
    public final AuditLogService auditLogService;

    public RbacTestWorld() {
        tenant = Tenant.builder().tenantId(tenantId).tenantName("Test University").tenantType("UNIVERSITY")
                .state(TenantState.ACTIVE).isolationMode(IsolationMode.SHARED).subdomain("test")
                .keycloakRealm("test-realm").build();

        lenient().when(actionCatalogRepository.findById(any())).thenAnswer(inv -> {
            ActionRow row = ACTIONS.get((String) inv.getArgument(0));
            return Optional.ofNullable(row).map(RbacTestWorld::catalogRow);
        });

        lenient().when(roleDefinitionRepository.findById(any())).thenAnswer(inv -> {
            RoleDefinitionId id = inv.getArgument(0);
            return Optional.ofNullable(id.getTenantId().equals(tenantId) ? roles.get(id.getRoleDefinitionId()) : null);
        });
        lenient().when(roleDefinitionRepository.findByIdTenantId(any())).thenAnswer(inv ->
                tenantId.equals(inv.getArgument(0)) ? new ArrayList<>(roles.values()) : List.of());
        lenient().when(roleDefinitionRepository.existsByIdTenantIdAndRoleCode(any(), any())).thenAnswer(inv ->
                roles.values().stream().anyMatch(r -> r.getRoleCode().equals(inv.getArgument(1))));
        lenient().when(roleDefinitionRepository.findByIdTenantIdAndRoleCode(any(), any())).thenAnswer(inv ->
                roles.values().stream().filter(r -> r.getRoleCode().equals(inv.getArgument(1))).findFirst());
        lenient().when(roleDefinitionRepository.save(any())).thenAnswer(inv -> {
            RoleDefinition r = inv.getArgument(0);
            roles.put(r.getId().getRoleDefinitionId(), r);
            return r;
        });

        lenient().when(roleActionRepository.existsByIdTenantIdAndRoleDefinitionIdAndActionCode(any(), any(), any()))
                .thenAnswer(inv -> tenantId.equals(inv.getArgument(0)) && roleActions.stream().anyMatch(ra ->
                        ra.getRoleDefinitionId().equals(inv.getArgument(1)) && ra.getActionCode().equals(inv.getArgument(2))));
        lenient().when(roleActionRepository.findByIdTenantIdAndRoleDefinitionId(any(), any())).thenAnswer(inv ->
                roleActions.stream().filter(ra -> ra.getRoleDefinitionId().equals(inv.getArgument(1))).toList());
        lenient().when(roleActionRepository.save(any())).thenAnswer(inv -> {
            RoleAction ra = inv.getArgument(0);
            roleActions.add(ra);
            return ra;
        });

        lenient().when(userRoleAssignmentRepository
                        .findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
                                any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> assignments.stream().filter(a ->
                        tenantId.equals(inv.getArgument(0))
                                && a.getUserAccountId().equals(inv.getArgument(1))
                                && a.getScopeType() == inv.getArgument(2)
                                && Objects.equals(a.getInstitutionId(), inv.getArgument(3))
                                && Objects.equals(a.getDepartmentId(), inv.getArgument(4))
                                && a.getStatus() == inv.getArgument(5)).toList());
        lenient().when(userRoleAssignmentRepository.findAllByIdTenantIdAndUserAccountIdAndStatusOrderByEffectiveFromAsc(any(), any(), any()))
                .thenAnswer(inv -> byUserAndStatus(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        lenient().when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountIdAndStatus(any(), any(), any()))
                .thenAnswer(inv -> byUserAndStatus(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        lenient().when(userRoleAssignmentRepository.findByIdTenantIdAndUserAccountId(any(), any()))
                .thenAnswer(inv -> tenantId.equals(inv.getArgument(0))
                        ? assignments.stream().filter(a -> a.getUserAccountId().equals(inv.getArgument(1))).toList()
                        : List.of());
        lenient().when(userRoleAssignmentRepository.findByIdTenantIdAndIdUserRoleAssignmentId(any(), any()))
                .thenAnswer(inv -> tenantId.equals(inv.getArgument(0))
                        ? assignments.stream().filter(a -> a.getId().getUserRoleAssignmentId().equals(inv.getArgument(1))).findFirst()
                        : Optional.empty());

        lenient().when(userActiveRoleRepository.findByIdTenantIdAndIdUserAccountId(any(), any())).thenAnswer(inv ->
                tenantId.equals(inv.getArgument(0)) ? Optional.ofNullable(activeChoices.get((UUID) inv.getArgument(1))) : Optional.empty());
        lenient().when(userActiveRoleRepository.findById(any())).thenAnswer(inv -> {
            UserAccountId id = inv.getArgument(0);
            return tenantId.equals(id.getTenantId()) ? Optional.ofNullable(activeChoices.get(id.getUserAccountId())) : Optional.empty();
        });
        lenient().when(userActiveRoleRepository.save(any())).thenAnswer(inv -> {
            UserActiveRole r = inv.getArgument(0);
            activeChoices.put(r.getId().getUserAccountId(), r);
            return r;
        });

        lenient().when(userAccountRepository.findById(any())).thenAnswer(inv -> {
            UserAccountId id = inv.getArgument(0);
            return tenantId.equals(id.getTenantId()) ? Optional.ofNullable(accounts.get(id.getUserAccountId())) : Optional.empty();
        });
        lenient().when(userAccountRepository.save(any())).thenAnswer(inv -> {
            UserAccount a = inv.getArgument(0);
            accounts.put(a.getId().getUserAccountId(), a);
            return a;
        });

        lenient().when(standardRoleRepository.findAll()).thenAnswer(inv ->
                STANDARD_ROLES.values().stream().map(RbacTestWorld::standardRoleRow).toList());
        lenient().when(standardRoleRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(STANDARD_ROLES.get((String) inv.getArgument(0))).map(RbacTestWorld::standardRoleRow));

        lenient().when(auditLogRepository.save(any())).thenAnswer(inv -> {
            AuditLog row = inv.getArgument(0);
            auditRows.add(row);
            return row;
        });

        evaluator = new PermissionEvaluatorService(actionCatalogRepository, userRoleAssignmentRepository,
                roleActionRepository, roleDefinitionRepository, null, userActiveRoleRepository);
        auditLogService = new AuditLogService(auditLogRepository);
    }

    private List<UserRoleAssignment> byUserAndStatus(UUID tId, UUID userId, AssignmentStatus status) {
        if (!tenantId.equals(tId)) {
            return List.of();
        }
        return assignments.stream().filter(a -> a.getUserAccountId().equals(userId) && a.getStatus() == status).toList();
    }

    // ----------------------------------------------------------------------- builders

    /** The tenant's copy of a standard role, with exactly the actions D3 gives it. */
    public RoleDefinition standardRole(String roleCode) {
        Optional<RoleDefinition> existing = roles.values().stream().filter(r -> r.getRoleCode().equals(roleCode)).findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        StandardRoleRow sr = Objects.requireNonNull(STANDARD_ROLES.get(roleCode), "not a standard role: " + roleCode);
        RoleDefinition rd = RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, UUID.randomUUID()))
                .roleCode(roleCode).roleName(sr.name()).roleOrigin(RoleOrigin.STANDARD).roleLevel(sr.level())
                .baseStandardRole(roleCode).status(RoleDefinitionStatus.ACTIVE)
                .ownerLevel(sr.level() == RoleLevel.EXTERNAL ? null : sr.level().name())
                .createdBy(UUID.randomUUID()).updatedBy(UUID.randomUUID()).build();
        roles.put(rd.getId().getRoleDefinitionId(), rd);
        for (String action : STANDARD_ROLE_ACTIONS.getOrDefault(roleCode, List.of())) {
            grant(rd, action);
        }
        return rd;
    }

    /** A custom role owned at the given level (and institution), holding the given actions. */
    public RoleDefinition customRole(String roleCode, String ownerLevel, UUID ownerInstitutionId, String... actions) {
        RoleDefinition rd = RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, UUID.randomUUID()))
                .roleCode(roleCode).roleName("Custom " + roleCode).roleOrigin(RoleOrigin.CUSTOM)
                .roleLevel(ownerInstitutionId == null ? RoleLevel.UNIVERSITY : RoleLevel.COLLEGE)
                .customScope(ownerInstitutionId == null ? CustomScope.UNIVERSITY_OFFICE : CustomScope.INSTITUTION)
                .status(RoleDefinitionStatus.ACTIVE).ownerLevel(ownerLevel).ownerInstitutionId(ownerInstitutionId)
                .createdBy(UUID.randomUUID()).updatedBy(UUID.randomUUID()).build();
        roles.put(rd.getId().getRoleDefinitionId(), rd);
        for (String a : actions) {
            grant(rd, a);
        }
        return rd;
    }

    public void grant(RoleDefinition rd, String actionCode) {
        roleActions.add(RoleAction.builder().id(new RoleActionId(tenantId, UUID.randomUUID()))
                .roleDefinitionId(rd.getId().getRoleDefinitionId()).actionCode(actionCode).build());
    }

    public UUID user(String displayName) {
        UUID id = UUID.randomUUID();
        accounts.put(id, UserAccount.builder().id(new UserAccountId(tenantId, id)).userType(UserType.STAFF)
                .displayName(displayName).email(displayName.toLowerCase().replace(' ', '.') + "@test.edu")
                .status(UserAccountStatus.ACTIVE).createdBy(id).updatedBy(id).build());
        return id;
    }

    public UserRoleAssignment assign(UUID userId, String roleCode, ScopeType scopeType, UUID institutionId, UUID departmentId) {
        return assign(userId, standardRole(roleCode), scopeType, institutionId, departmentId);
    }

    public UserRoleAssignment assign(UUID userId, RoleDefinition role, ScopeType scopeType, UUID institutionId, UUID departmentId) {
        UserRoleAssignment a = UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                .userAccountId(userId).roleDefinitionId(role.getId().getRoleDefinitionId())
                .scopeType(scopeType).institutionId(institutionId).departmentId(departmentId)
                .effectiveFrom(Instant.now().minusSeconds(3600)).status(AssignmentStatus.ACTIVE)
                .createdBy(userId).updatedBy(userId).build();
        assignments.add(a);
        return a;
    }

    /** Saves an active-role choice the way POST /me/active-role does (app.user_active_role). */
    public void choose(UUID userId, UserRoleAssignment assignment) {
        Instant now = Instant.now();
        activeChoices.put(userId, UserActiveRole.builder().id(new UserAccountId(tenantId, userId))
                .userRoleAssignmentId(assignment.getId().getUserRoleAssignmentId())
                .switchedAt(now).createdAt(now).updatedAt(now).build());
    }

    public RbacPrincipal principal(UUID userId) {
        return new RbacPrincipal(tenantId, userId);
    }

    /**
     * Runs the real TenantUserLookup for this user (as the bearer-token resolver does on every
     * request), so TenantContext carries the active role exactly as in production.
     */
    public void signIn(UUID userId) {
        String subject = "kc-" + userId;
        UserIdentityLink link = UserIdentityLink.builder().id(new UserIdentityLinkId(tenantId, UUID.randomUUID()))
                .userAccountId(userId).identityRealm(tenant.getKeycloakRealm()).identitySubject(subject)
                .usernameKind(UsernameKind.EMAIL).status(IdentityLinkStatus.ACTIVE).build();
        lenient().when(userIdentityLinkRepository.findByIdTenantIdAndIdentityRealmAndIdentitySubjectAndStatus(
                tenantId, tenant.getKeycloakRealm(), subject, IdentityLinkStatus.ACTIVE)).thenReturn(Optional.of(link));
        new TenantUserLookup(userIdentityLinkRepository, userAccountRepository, userRoleAssignmentRepository,
                roleDefinitionRepository, userActiveRoleRepository)
                .resolveActiveUser(tenant, tenant.getKeycloakRealm(), subject);
    }

    public void signOut() {
        TenantContext.clear();
    }

    public List<RoleAction> actionsOf(UUID roleDefinitionId) {
        return roleActions.stream().filter(ra -> ra.getRoleDefinitionId().equals(roleDefinitionId)).toList();
    }

    public Set<UUID> institutionsInContext() {
        return TenantContext.currentInstitutionIds();
    }

    // ----------------------------------------------------------------------- row factories

    private static ActionCatalog catalogRow(ActionRow row) {
        ActionCatalog a = new ActionCatalog();
        set(a, "actionCode", row.code());
        set(a, "approvalAction", row.approval());
        set(a, "sensitiveClass", row.sensitiveClass());
        return a;
    }

    private static StandardRole standardRoleRow(StandardRoleRow row) {
        StandardRole r = new StandardRole();
        set(r, "roleCode", row.code());
        set(r, "roleName", row.name());
        set(r, "roleLevel", row.level());
        return r;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
