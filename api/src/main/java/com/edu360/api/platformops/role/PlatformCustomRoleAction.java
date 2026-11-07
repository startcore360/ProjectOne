package com.edu360.api.platformops.role;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "platform", name = "platform_role_action")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformCustomRoleAction {

    @Id
    @Column(name = "platform_role_action_id")
    private UUID platformRoleActionId;

    @Column(name = "platform_role_id", nullable = false)
    private UUID platformRoleId;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_mode", nullable = false)
    private AccessMode accessMode;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
