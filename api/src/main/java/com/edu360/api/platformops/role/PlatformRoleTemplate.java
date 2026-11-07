package com.edu360.api.platformops.role;

import java.util.UUID;

import com.edu360.api.platform.PlatformRole;

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
@Table(schema = "platform", name = "platform_role_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformRoleTemplate {

    @Id
    @Column(name = "platform_role_template_id")
    private UUID platformRoleTemplateId;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_platform_role", nullable = false)
    private PlatformRole basePlatformRole;

    @Column(name = "is_standard", nullable = false)
    private boolean standard;

    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    /** ACTIVE or ARCHIVED (ck_platform_role_template_status). */
    @Column(nullable = false)
    private String status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
}
