package com.edu360.api.platformops.release;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="release",schema="platform") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PlatformRelease {
 @Id @Column(name="release_id",nullable=false) private UUID releaseId;
 @Column(name="version_label",nullable=false,length=40) private String versionLabel;
 @Column(name="release_notes",nullable=false,length=20000) private String releaseNotes;
 @Column(name="is_hotfix",nullable=false) private boolean hotfix;
 @Column(name="status",nullable=false) private String status;
 @Column(name="deployed_at") private Instant deployedAt;
 @Column(name="scheduled_at") private Instant scheduledAt;
 @Column(name="rolled_back_at") private Instant rolledBackAt;
 @Column(name="schema_version",nullable=false,length=60) private String schemaVersion;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="created_by",nullable=false) private UUID createdBy;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Column(name="updated_by",nullable=false) private UUID updatedBy;
 @Column(name="row_version",nullable=false) private Integer rowVersion;
}
