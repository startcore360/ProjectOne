package com.edu360.api.selfservice;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notice", schema = "app")
@Getter
@Setter
@NoArgsConstructor
public class Notice {

    @EmbeddedId
    private NoticeId id;

    @Column(name = "notice_level", nullable = false)
    private String noticeLevel;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "notice_kind", nullable = false)
    private String noticeKind;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "authored_by_user_id", nullable = false)
    private UUID authoredByUserId;

    @Column(name = "authored_active_role", nullable = false)
    private String authoredActiveRole;

    @Column(name = "publish_path", nullable = false)
    private String publishPath;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Notice(UUID tenantId, UUID noticeId, String noticeLevel, String noticeKind,
                  String title, String body, UUID authoredByUserId, String authoredActiveRole,
                  String publishPath, String status) {
        this.id = new NoticeId(tenantId, noticeId);
        this.noticeLevel = noticeLevel;
        this.noticeKind = noticeKind;
        this.title = title;
        this.body = body;
        this.authoredByUserId = authoredByUserId;
        this.authoredActiveRole = authoredActiveRole;
        this.publishPath = publishPath;
        this.status = status != null ? status : "PUBLISHED";
        this.publishedAt = Instant.now();
        this.createdAt = Instant.now();
    }
}
