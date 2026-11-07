package com.edu360.api.platformops.security;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "setting_value", schema = "platform")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettingValue {

    @Id
    @Column(name = "setting_value_id", nullable = false)
    private UUID settingValueId;

    @Column(name = "setting_key", nullable = false)
    private String settingKey;

    @Column(name = "value", nullable = false, columnDefinition = "jsonb")
    private String value;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "change_reason", nullable = false)
    private String changeReason;

    @Column(name = "restored_from_version_no")
    private Integer restoredFromVersionNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;
}
