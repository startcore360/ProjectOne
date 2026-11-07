package com.edu360.api.platformops.role;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A recorded, time-limited step-in (TPA-02). Append-only: there are deliberately no setters. */
@Entity
@Table(schema = "platform", name = "platform_step_in")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformStepIn {

    @Id
    @Column(name = "platform_step_in_id")
    private UUID platformStepInId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(nullable = false)
    private String reason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
