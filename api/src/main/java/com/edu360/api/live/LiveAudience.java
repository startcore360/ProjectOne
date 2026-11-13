package com.edu360.api.live;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@Component
public class LiveAudience {
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;

    public LiveAudience(JdbcTemplate jdbc, PermissionEvaluatorService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    /** Resolve recipients at send time; only users whose active role currently holds the action qualify. */
    public List<UUID> recipients(UUID tenantId, UUID institutionId, UUID departmentId, String actionCode) {
        List<UUID> candidates = jdbc.query("""
                SELECT DISTINCT a.user_account_id
                  FROM app.user_role_assignment a
                 WHERE a.tenant_id = ? AND a.status = 'ACTIVE'
                   AND (a.institution_id = ? OR a.institution_id IS NULL)
                   AND (a.department_id = ? OR a.department_id IS NULL)
                """, (rs, row) -> rs.getObject(1, UUID.class),
                tenantId, institutionId, departmentId);
        return candidates.stream().filter(userId -> {
            RbacPrincipal principal = new RbacPrincipal(tenantId, userId);
            RbacScope scope = permissions.targetScope(principal, institutionId, departmentId);
            return permissions.can(principal, actionCode, scope, RbacTarget.NONE).allowed();
        }).distinct().toList();
    }
}
