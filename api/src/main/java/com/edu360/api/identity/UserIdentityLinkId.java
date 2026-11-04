package com.edu360.api.identity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite key (tenant_id, user_identity_link_id) - see UserAccountId for why tenant_id is
 *  part of the key itself rather than just a plain foreign-key column. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserIdentityLinkId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_identity_link_id", nullable = false)
    private UUID userIdentityLinkId;
}
