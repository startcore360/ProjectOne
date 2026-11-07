package com.edu360.api.termlock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.termlock.dto.LockTermRequest;
import com.edu360.api.termlock.dto.TermLockResponse;
import com.edu360.api.termlock.dto.UnlockTermRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TermLockService {

    private static final Logger log = LoggerFactory.getLogger(TermLockService.class);

    private final TermLockRepository termLockRepository;
    private final TermLockEventRepository termLockEventRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<TermLockResponse> listTermLocks(UUID tenantId, UUID institutionId) {
        return termLockRepository.findByIdTenantIdAndInstitutionId(tenantId, institutionId)
                .stream()
                .map(TermLockResponse::from)
                .toList();
    }

    @Transactional
    public TermLockResponse lockTerm(UUID tenantId, UUID institutionId, TenantPrincipal principal, LockTermRequest request) {
        // Fail-closed authorization check: TERM_LOCK on own institution
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "TERM_LOCK",
                RbacScope.institution(institutionId),
                RbacTarget.NONE
        );

        TermLock termLock = termLockRepository.findByIdTenantIdAndInstitutionIdAndAcademicSessionIdAndSemesterId(
                tenantId, institutionId, request.academicSessionId(), request.semesterId())
                .orElse(null);

        if (termLock == null) {
            UUID termLockId = UUID.randomUUID();
            termLock = TermLock.builder()
                    .id(new TermLockId(tenantId, termLockId))
                    .institutionId(institutionId)
                    .academicSessionId(request.academicSessionId())
                    .semesterId(request.semesterId())
                    .lockState("LOCKED")
                    .unlockUntil(null)
                    .createdBy(principal.userAccountId())
                    .updatedBy(principal.userAccountId())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .rowVersion(1)
                    .build();
        } else {
            termLock.setLockState("LOCKED");
            termLock.setUnlockUntil(null);
            termLock.setUpdatedAt(Instant.now());
            termLock.setUpdatedBy(principal.userAccountId());
            termLock.setRowVersion(termLock.getRowVersion() != null ? termLock.getRowVersion() + 1 : 1);
        }

        TermLock saved = termLockRepository.save(termLock);

        // Record audit event in app.term_lock_event
        UUID eventId = UUID.randomUUID();
        TermLockEvent event = TermLockEvent.builder()
                .id(new TermLockEventId(tenantId, eventId))
                .institutionId(institutionId)
                .termLockId(saved.getTermLockId())
                .eventType("LOCKED")
                .actorUserId(principal.userAccountId())
                .notes(request.notes())
                .createdBy(principal.userAccountId())
                .createdAt(Instant.now())
                .build();
        termLockEventRepository.save(event);

        auditLogService.record(
                AuditEventType.TERM_LOCKED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Locked term " + saved.getTermLockId() + " in institution " + institutionId
        );

        log.info("TermLock {} locked by {} for institution {}", saved.getTermLockId(), principal.userAccountId(), institutionId);

        return TermLockResponse.from(saved);
    }

    @Transactional
    public TermLockResponse unlockTerm(UUID tenantId, UUID institutionId, UUID termLockId, TenantPrincipal principal, UnlockTermRequest request) {
        // Fail-closed authorization check: TERM_UNLOCK on own institution
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "TERM_UNLOCK",
                RbacScope.institution(institutionId),
                RbacTarget.NONE
        );

        // Rule AT-09: Reason required
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessRuleViolationException("REASON_REQUIRED", "Unlock reason is required under Rule AT-09.");
        }

        // Rule AT-09: Lasts at most 3 days (72 hours) then relocks
        int hours = request.unlockDurationHours() != null ? request.unlockDurationHours() : 72;
        if (hours <= 0 || hours > 72) {
            throw new BusinessRuleViolationException("INVALID_DURATION", "Term unlock duration cannot exceed 72 hours (3 days) under Rule AT-09.");
        }

        TermLock termLock = termLockRepository.findByIdTenantIdAndIdTermLockId(tenantId, termLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Term lock not found: " + termLockId));

        if (!termLock.getInstitutionId().equals(institutionId)) {
            throw new BusinessRuleViolationException("INSTITUTION_MISMATCH", "Term lock does not belong to the specified institution.");
        }

        Instant unlockUntil = Instant.now().plus(hours, ChronoUnit.HOURS);
        termLock.setLockState("TEMP_UNLOCKED");
        termLock.setUnlockUntil(unlockUntil);
        termLock.setUpdatedAt(Instant.now());
        termLock.setUpdatedBy(principal.userAccountId());
        termLock.setRowVersion(termLock.getRowVersion() != null ? termLock.getRowVersion() + 1 : 1);

        TermLock saved = termLockRepository.save(termLock);

        // Record audit event in app.term_lock_event
        UUID eventId = UUID.randomUUID();
        TermLockEvent event = TermLockEvent.builder()
                .id(new TermLockEventId(tenantId, eventId))
                .institutionId(institutionId)
                .termLockId(saved.getTermLockId())
                .eventType("UNLOCKED")
                .actorUserId(principal.userAccountId())
                .notes(request.reason())
                .createdBy(principal.userAccountId())
                .createdAt(Instant.now())
                .build();
        termLockEventRepository.save(event);

        auditLogService.record(
                AuditEventType.TERM_UNLOCKED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Unlocked term " + termLockId + " until " + unlockUntil + " reason: " + request.reason()
        );

        log.info("TermLock {} unlocked by {} until {} in institution {}", termLockId, principal.userAccountId(), unlockUntil, institutionId);

        return TermLockResponse.from(saved);
    }
}
