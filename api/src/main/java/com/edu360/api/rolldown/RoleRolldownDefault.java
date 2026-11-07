package com.edu360.api.rolldown;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "role_rolldown_default", schema = "ref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleRolldownDefault {

    @Id
    @Column(name = "role_rolldown_default_id", nullable = false)
    private Integer id;

    @Column(name = "missing_role_code", nullable = false)
    private String missingRoleCode;

    @Column(name = "context", nullable = false)
    private String context;

    @Column(name = "permission_kind", nullable = false)
    private String permissionKind;

    @Column(name = "preference", nullable = false)
    private short preference;

    @Column(name = "stand_in_role_code", nullable = false)
    private String standInRoleCode;

    @Column(name = "note")
    private String note;
}
