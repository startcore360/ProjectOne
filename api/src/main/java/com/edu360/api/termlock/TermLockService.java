package com.edu360.api.termlock;

import java.time.Instant;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.edu360.api.tenant.TenantContext;
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
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    public void setDataSource(DataSource dataSource) { this.jdbcTemplate = new JdbcTemplate(dataSource); }

    /** Shared date-based lock check; mirrors app.is_term_locked used by SQL guards. */
    @Transactional(readOnly = true)
    public boolean isLocked(UUID tenantId, UUID sectionId, LocalDate date) {
        if (tenantId == null || sectionId == null || date == null || !tenantId.equals(TenantContext.currentTenantId()))
            throw new ResourceNotFoundException("Term lock scope not found");
        if (jdbcTemplate == null) throw new IllegalStateException("Term-lock JDBC function is unavailable.");
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT app.is_term_locked(?, ?, ?)", Boolean.class, tenantId, sectionId, date));
    }

    @Transactional(readOnly = true)
    public List<TermLockResponse> listTermLocks(UUID tenantId, UUID institutionId, TenantPrincipal principal) {
        if (principal != null && institutionId != null) {
            permissionEvaluatorService.authorize(
                    new RbacPrincipal(tenantId, principal.userAccountId()),
                    "TERM_LOCK",
                    RbacScope.institution(institutionId),
                    RbacTarget.NONE
            );
        }
        return termLockRepository.findByIdTenantIdAndInstitutionId(tenantId, institutionId)
                .stream()
                .map(TermLockResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TermLockResponse> listTermLocks(UUID tenantId, UUID institutionId) {
        return listTermLocks(tenantId, institutionId, null);
    }

    @Transactional(readOnly = true)
    public TermLockResponse getTermLock(UUID tenantId, UUID termLockId) {
        TermLock termLock = termLockRepository.findByIdTenantIdAndIdTermLockId(tenantId, termLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Term lock not found: " + termLockId));
        return TermLockResponse.from(termLock);
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
                .eventType("LOCK")
                .actorUserId(principal.userAccountId())
                .notes(request.notes())
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
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

        // Rule AT-09: Block repeated re-unlocking while already active
        if ("TEMP_UNLOCKED".equals(termLock.getLockState()) && termLock.getUnlockUntil() != null
                && termLock.getUnlockUntil().isAfter(Instant.now())) {
            throw new BusinessRuleViolationException("ALREADY_UNLOCKED",
                    "Term is currently temporarily unlocked until " + termLock.getUnlockUntil() + "; repeated re-unlocking is prohibited under Rule AT-09.");
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
                .eventType("UNLOCK")
                .actorUserId(principal.userAccountId())
                .notes(request.reason())
                .unlockUntil(unlockUntil)
                .occurredAt(Instant.now())
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
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
