package com.edu360.api.identity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WHAT: the composite primary key of app.user_account - (tenant_id, user_account_id) together,
 * never user_account_id alone.
 * WHY tenant_id is part of the key at all (not just a foreign key column): every app.* table in
 * the V4.0 schema does this on purpose (schema comment: "first column of every composite FK,
 * PTD-02/PTD-03") so that a foreign key from any other tenant-scoped table is ALSO forced to
 * carry the correct tenant_id, or the database rejects it outright. Row-level security on top of
 * that is defence in depth, not the only thing stopping a cross-tenant join.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserAccountId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_account_id", nullable = false)
    private UUID userAccountId;
}
