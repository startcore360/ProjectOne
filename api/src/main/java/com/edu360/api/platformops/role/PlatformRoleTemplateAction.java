package com.edu360.api.platformops.role;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "platform", name = "platform_role_template_action")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformRoleTemplateAction {

    @Id
    @Column(name = "platform_role_template_action_id")
    private UUID platformRoleTemplateActionId;

    @Column(name = "platform_role_template_id", nullable = false)
    private UUID platformRoleTemplateId;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
