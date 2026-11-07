package com.edu360.api.termlock;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "app", name = "term_lock_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermLockEvent {

    @EmbeddedId
    private TermLockEventId id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "term_lock_id", nullable = false)
    private UUID termLockId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
