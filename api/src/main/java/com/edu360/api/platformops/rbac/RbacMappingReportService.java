package com.edu360.api.platformops.rbac;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.role.PlatformCustomRole;
import com.edu360.api.platformops.role.PlatformCustomRoleActionRepository;
import com.edu360.api.platformops.role.PlatformCustomRoleRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

@Service
public class RbacMappingReportService {
    private static final Set<String> PLATFORM_CUSTOM_NEVER_ALLOWED = Set.of(
            "TENANT_EXPORT_APPROVE", "TENANT_DELETION_APPROVE", "RETENTION_CHANGE_APPROVE",
            "APPROVAL_DELEGATE", "PLATFORM_USER_MANAGE", "TENANT_DELETION_CONFIRM");

    private final JdbcTemplate tenantJdbc;
    private final TenantRepository tenants;
    private final PlatformCustomRoleRepository platformRoles;
    private final PlatformCustomRoleActionRepository platformActions;
    private final TransactionTemplate tenantReadOnlyTransaction;

    public RbacMappingReportService(JdbcTemplate tenantJdbc, TenantRepository tenants,
            PlatformCustomRoleRepository platformRoles, PlatformCustomRoleActionRepository platformActions,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        this.tenantJdbc = tenantJdbc;
        this.tenants = tenants;
        this.platformRoles = platformRoles;
        this.platformActions = platformActions;
        this.tenantReadOnlyTransaction = new TransactionTemplate(transactionManager);
        this.tenantReadOnlyTransaction.setReadOnly(true);
    }

    public RbacMappingReport generate() {
        List<RbacMappingReportRow> mismatches = new ArrayList<>();
        List<RbacMappingReportRow> noBase = new ArrayList<>();
        List<RbacMappingReport.SkippedTenant> skipped = new ArrayList<>();
        comparePlatformRoles(mismatches);
        for (Tenant tenant : tenants.findAll()) {
            if (tenant.getState() == TenantState.DELETED || tenant.getState() == TenantState.CREATED) {
                skipped.add(new RbacMappingReport.SkippedTenant(tenant.getTenantId(), tenant.getTenantName(),
                        tenant.getState().name()));
                continue;
            }
            TenantContext.set(tenant.getTenantId(), tenant.getIsolationMode(), tenant.getDbConnectionRef());
            try {
                tenantReadOnlyTransaction.executeWithoutResult(status -> compareTenant(tenant, mismatches, noBase));
            } finally {
                TenantContext.clear();
            }
        }
        return new RbacMappingReport(List.copyOf(mismatches), List.copyOf(noBase), List.copyOf(skipped));
    }

    private void compareTenant(Tenant tenant, List<RbacMappingReportRow> mismatches,
            List<RbacMappingReportRow> noBase) {
        // Move this read-only comparison to the edu_report read-only login once that connection exists.
        List<RoleSnapshot> roles = tenantJdbc.query("""
                SELECT rd.role_definition_id, rd.role_code, rd.role_name, rd.role_origin,
                       rd.base_standard_role, ra.action_code
                  FROM app.role_definition rd
                  LEFT JOIN app.role_action ra
                    ON ra.tenant_id = rd.tenant_id
                   AND ra.role_definition_id = rd.role_definition_id
                 WHERE rd.tenant_id = ?
                 ORDER BY rd.role_code, ra.action_code
                """, (rs, row) -> new RoleSnapshot(
                    rs.getObject("role_definition_id", UUID.class), rs.getString("role_code"),
                    rs.getString("role_name"), rs.getString("role_origin"),
                    rs.getString("base_standard_role"), rs.getString("action_code")), tenant.getTenantId());

        var grouped = roles.stream().collect(Collectors.groupingBy(RoleSnapshot::roleId));
        for (List<RoleSnapshot> roleRows : grouped.values()) {
            RoleSnapshot role = roleRows.get(0);
            Set<String> held = roleRows.stream().map(RoleSnapshot::actionCode)
                    .filter(code -> code != null).collect(Collectors.toSet());
            String baseline = role.baseRole() == null || role.baseRole().isBlank()
                    ? ("STANDARD".equals(role.origin()) ? role.roleCode() : null) : role.baseRole();
            if (baseline == null) {
                noBase.add(row(tenant, role, null, "NO_BASE_TEMPLATE",
                        "Custom role has no recorded base_standard_role; template comparison is unavailable."));
            } else {
                Set<String> template = new HashSet<>(tenantJdbc.queryForList(
                        "SELECT action_code FROM ref.standard_role_action WHERE role_code = ?",
                        String.class, baseline));
                for (String action : held) {
                    if (!template.contains(action)) {
                        mismatches.add(row(tenant, role, action, "BASE_TEMPLATE_DRIFT",
                                "Role holds an action absent from its base standard-role template."));
                    }
                }
            }
            for (String action : held) {
                Integer known = tenantJdbc.queryForObject(
                        "SELECT count(*) FROM ref.action_catalog WHERE action_code = ?", Integer.class, action);
                if (known == null || known == 0) {
                    mismatches.add(row(tenant, role, action, "UNKNOWN_ACTION",
                            "Action is missing from ref.action_catalog."));
                }
                String neverAllowed = tenantJdbc.queryForObject(
                        "SELECT app.fn_never_allowed_class(?)", String.class, action);
                boolean prohibited = "DATA_PROTECTION".equals(neverAllowed)
                        || ("DECISION_D".equals(neverAllowed)
                            && ("CUSTOM".equals(role.origin()) || baseline == null
                                || !tenantJdbc.queryForList(
                                        "SELECT action_code FROM ref.standard_role_action WHERE role_code = ? AND action_code = ?",
                                        String.class, baseline, action).contains(action)));
                if (prohibited) {
                    mismatches.add(row(tenant, role, action, "NEVER_ALLOWED_ACTION",
                            "Action is classified as never allowed: " + neverAllowed + "."));
                }
            }
        }
    }

    private void comparePlatformRoles(List<RbacMappingReportRow> mismatches) {
        for (PlatformRole role : PlatformRole.values()) {
            Set<String> expected = new HashSet<>(tenantJdbc.queryForList(
                    "SELECT action_code FROM ref.standard_role_action WHERE role_code = ?",
                    String.class, role.name()));
            Set<String> actual = PlatformPermissionService.standardActions(role);
            comparePlatformSet(role.name(), role.name(), expected, actual, mismatches);
            checkPlatformActions(role.name(), role.name(), actual, false, mismatches);
        }
        for (PlatformCustomRole role : platformRoles.findAllByOrderByRoleNameAsc()) {
            Set<String> expected = new HashSet<>(tenantJdbc.queryForList(
                    "SELECT action_code FROM ref.standard_role_action WHERE role_code = ?",
                    String.class, role.getBasePlatformRole().name()));
            Set<String> actual = platformActions.findByPlatformRoleId(role.getPlatformRoleId()).stream()
                    .map(a -> a.getActionCode()).collect(Collectors.toSet());
            comparePlatformSet(role.getBasePlatformRole().name(), role.getRoleName(), expected, actual, mismatches);
            checkPlatformActions(role.getBasePlatformRole().name(), role.getRoleName(), actual, true, mismatches);
        }
    }

    private void checkPlatformActions(String roleCode, String roleName, Set<String> actions, boolean custom,
            List<RbacMappingReportRow> rows) {
        for (String action : actions) {
            Integer known = tenantJdbc.queryForObject(
                    "SELECT count(*) FROM ref.action_catalog WHERE action_code = ?", Integer.class, action);
            if (known == null || known == 0) {
                rows.add(new RbacMappingReportRow("PLATFORM", null, "Platform", roleCode,
                        roleName, action, "UNKNOWN_ACTION", "Action is missing from ref.action_catalog."));
            }
            if (custom && PLATFORM_CUSTOM_NEVER_ALLOWED.contains(action)) {
                rows.add(new RbacMappingReportRow("PLATFORM", null, "Platform", roleCode,
                        roleName, action, "NEVER_ALLOWED_ACTION", "Action is classified as never allowed."));
            }
        }
    }

    private static void comparePlatformSet(String roleCode, String roleName, Set<String> expected,
            Set<String> actual, List<RbacMappingReportRow> rows) {
        for (String action : actual) {
            if (!expected.contains(action)) {
                rows.add(new RbacMappingReportRow("PLATFORM", null, "Platform", roleCode,
                        roleName, action, "BASE_TEMPLATE_DRIFT", "Role holds an action absent from its base platform-role matrix."));
            }
        }
    }

    private static RbacMappingReportRow row(Tenant tenant, RoleSnapshot role,
            String action, String result, String detail) {
        return new RbacMappingReportRow("TENANT", tenant.getTenantId(), tenant.getTenantName(),
                role.roleCode(), role.roleName(), action, result, detail);
    }

    private record RoleSnapshot(UUID roleId, String roleCode, String roleName, String origin,
            String baseRole, String actionCode) {}
}
