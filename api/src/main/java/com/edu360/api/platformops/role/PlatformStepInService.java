package com.edu360.api.platformops.role;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformAccessLookup;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.role.dto.StepInRequest;
import com.edu360.api.platformops.role.dto.StepInResponse;

import lombok.RequiredArgsConstructor;

/** Lets the Product Owner act on a handed-over action for 30 minutes, with a recorded reason (TPA-02). */
@Service
@RequiredArgsConstructor
public class PlatformStepInService {

    static final Duration WINDOW = Duration.ofMinutes(30);

    private final PlatformStepInRepository stepInRepository;
    private final PlatformAccessLookup accessLookup;
    private final PlatformPermissionService permissionService;
    private final PlatformAuditWriter auditWriter;

    @Transactional(transactionManager = "platformTransactionManager")
    public StepInResponse stepIn(StepInRequest request, PlatformPrincipal actor) {
        if (actor.platformRole() != PlatformRole.PRODUCT_OWNER) {
            throw new UnauthorizedException("Only the Product Owner can step in.");
        }
        String action = request.actionCode();
        if (!permissionService.holds(actor, action)) {
            throw new UnauthorizedException("You don't hold " + action + ".");
        }
        if (!accessLookup.isHandedOver(action)) {
            throw new BusinessRuleViolationException(action + " has not been handed over, so you can act on it directly.");
        }
        Instant now = Instant.now();
        PlatformStepIn saved = stepInRepository.save(PlatformStepIn.builder()
                .platformStepInId(UUID.randomUUID())
                .actorUserId(actor.platformUserId())
                .actionCode(action)
                .reason(request.reason().trim())
                .startedAt(now)
                .expiresAt(now.plus(WINDOW))
                .build());
        auditWriter.record(actor, "UPDATE", action, "platform_step_in", saved.getPlatformStepInId());
        return StepInResponse.from(saved);
    }

    public List<StepInResponse> recent() {
        return stepInRepository.findTop50ByOrderByStartedAtDesc().stream().map(StepInResponse::from).toList();
    }
}
