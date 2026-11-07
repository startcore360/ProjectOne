package com.edu360.api.platformops.role;

import java.time.Instant;
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
@Table(schema = "platform", name = "platform_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformCustomRole {

    @Id
    @Column(name = "platform_role_id")
    private UUID platformRoleId;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_platform_role", nullable = false)
    private PlatformRole basePlatformRole;

    @Column(name = "source_template_id")
    private UUID sourceTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformCustomRoleStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
}
