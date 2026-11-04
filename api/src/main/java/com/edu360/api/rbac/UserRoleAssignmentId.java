package com.edu360.api.rbac;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** WHAT: the composite primary key of app.user_role_assignment - (tenant_id, user_role_assignment_id). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserRoleAssignmentId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_role_assignment_id", nullable = false)
    private UUID userRoleAssignmentId;
}
