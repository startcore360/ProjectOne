package com.edu360.api.platformops;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.edu360.api.platformops.dto.TenantProvisioningStatusResponse;
import com.edu360.api.platformops.dto.TenantProvisioningStepResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Coordinates resumable tenant setup as an ordered saga.
 *
 * Step handlers are supplied by the onboarding command when it is wired into
 * TenantManagementService. Keeping execution actions outside this class allows each external
 * system operation to run without holding a database transaction open. Step state is committed
 * independently before and after each action, so a process interruption leaves a resumable record.
 *
 * The execution order is intentionally independent of the persisted D4 step-code numbering.
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);
    private static final String FAILED_ERROR_CODE = "PROVISIONING_STEP_FAILED";

    private static final List<StepDefinition> EXECUTION_ORDER = List.of(
            new StepDefinition(1, "P01", "P01_REGISTRY", "Create tenant registry and local tenant anchor"),
            new StepDefinition(2, "P02", "P09_RETENTION", "Apply the tenant retention policy"),
            new StepDefinition(3, "P03", "P05_ENTITLEMENTS", "Apply plan modules, features and limits"),
            new StepDefinition(4, "P04", "P06_IDENTITY_REALM", "Create the tenant Keycloak realm"),
            new StepDefinition(5, "P05", "P10_REALM_POLICY", "Apply the current realm security policy"),
            new StepDefinition(6, "P06", "P07_FIRST_ADMIN", "Provision the first tenant administrator"),
            new StepDefinition(7, "P07", "P04_ROLES_SEED", "Seed the tenant standard roles")
    );

    @PersistenceContext
    private EntityManager entityManager;

    private final PlatformTransactionManager platformTransactionManager;

    public TenantProvisioningService(
            @Qualifier("platformTransactionManager") PlatformTransactionManager platformTransactionManager) {
        this.platformTransactionManager = platformTransactionManager;
    }

    /** Persist all seven step rows without overwriting progress from an earlier attempt. */
    public void initialize(UUID tenantId, UUID actorId) {
        requireIds(tenantId, actorId);
        inPlatformTransaction(() -> {
            Instant now = Instant.now();
            for (StepDefinition step : EXECUTION_ORDER) {
                entityManager.createNativeQuery(
                                "INSERT INTO platform.tenant_provision_step "
                                        + "(tenant_provision_step_id, tenant_id, step_code, status, attempts, "
                                        + "created_at, created_by, updated_at, updated_by, row_version) "
                                        + "VALUES (:id, :tenantId, :stepCode, 'PENDING', 0, :now, :actor, :now, :actor, 1) "
                                        + "ON CONFLICT (tenant_id, step_code) DO NOTHING")
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("tenantId", tenantId)
                        .setParameter("stepCode", step.stepCode())
                        .setParameter("now", now)
                        .setParameter("actor", actorId)
                        .executeUpdate();
            }
            return null;
        });
    }

    /**
     * Runs all unfinished steps in the agreed order. A completed step is never repeated.
     * If a step fails, previously completed steps are compensated in reverse order.
     *
     * @param actions handlers keyed by persisted step code; each handler must be idempotent
     */
    public TenantProvisioningStatusResponse provision(
            UUID tenantId, UUID actorId, Map<String, StepAction> actions) {
        requireIds(tenantId, actorId);
        validateActions(actions);
        initialize(tenantId, actorId);

        List<StepDefinition> completedThisAttempt = new ArrayList<>();
        for (StepDefinition step : EXECUTION_ORDER) {
            String status = readStepStatus(tenantId, step.stepCode());
            if ("DONE".equals(status)) {
                continue;
            }

            markRunning(tenantId, actorId, step.stepCode());
            try {
                actions.get(step.stepCode()).execute();
                markDone(tenantId, actorId, step.stepCode());
                completedThisAttempt.add(step);
            } catch (Exception ex) {
                markFailed(tenantId, actorId, step.stepCode());
                log.error("Tenant provisioning failed for tenant {} at step {}", tenantId, step.stepCode());
                compensateCompleted(tenantId, actorId, actions, completedThisAttempt);
                return getStatus(tenantId);
            }
        }
        return getStatus(tenantId);
    }

    /** Retry resumes at the first step that is not DONE; COMPENSATED and FAILED steps are retried. */
    public TenantProvisioningStatusResponse retry(
            UUID tenantId, UUID actorId, Map<String, StepAction> actions) {
        return provision(tenantId, actorId, actions);
    }

    /** Returns the seven persisted steps in business execution order, never alphabetical order. */
    public TenantProvisioningStatusResponse getStatus(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        List<?> rows = inPlatformTransaction(() -> entityManager.createNativeQuery(
                        "SELECT step_code, status, attempts, last_error_code, started_at, finished_at "
                                + "FROM platform.tenant_provision_step WHERE tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getResultList());

        Map<String, StepState> states = new HashMap<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            states.put(values[0].toString(), new StepState(
                    values[1].toString(),
                    ((Number) values[2]).intValue(),
                    values[3] == null ? null : values[3].toString(),
                    toInstant(values[4]),
                    toInstant(values[5])));
        }

        List<TenantProvisioningStepResponse> steps = EXECUTION_ORDER.stream()
                .map(step -> {
                    StepState state = states.getOrDefault(step.stepCode(),
                            new StepState("PENDING", 0, null, null, null));
                    return new TenantProvisioningStepResponse(
                            step.executionOrder(), step.planStep(), step.stepCode(), step.description(),
                            state.status(), state.attempts(), state.lastErrorCode(),
                            state.startedAt(), state.finishedAt());
                })
                .toList();

        boolean failed = steps.stream().anyMatch(s -> "FAILED".equals(s.status()));
        boolean allDone = steps.stream().allMatch(s -> "DONE".equals(s.status()));
        boolean running = steps.stream().anyMatch(s -> "RUNNING".equals(s.status()));
        boolean retryable = failed || steps.stream().anyMatch(s -> "COMPENSATED".equals(s.status()));
        String overall = allDone ? "DONE" : failed ? "FAILED" : running ? "RUNNING" : "PENDING";
        return new TenantProvisioningStatusResponse(tenantId, overall, retryable, steps);
    }

    public static List<String> executionStepCodes() {
        return EXECUTION_ORDER.stream().map(StepDefinition::stepCode).toList();
    }

    private void validateActions(Map<String, StepAction> actions) {
        if (actions == null) {
            throw new IllegalArgumentException("Provisioning step actions are required.");
        }
        for (StepDefinition step : EXECUTION_ORDER) {
            if (actions.get(step.stepCode()) == null) {
                throw new IllegalArgumentException("Missing provisioning action for " + step.stepCode());
            }
        }
    }

    private void compensateCompleted(UUID tenantId, UUID actorId, Map<String, StepAction> actions,
                                     List<StepDefinition> completed) {
        for (int i = completed.size() - 1; i >= 0; i--) {
            StepDefinition step = completed.get(i);
            try {
                actions.get(step.stepCode()).compensate();
                markCompensated(tenantId, actorId, step.stepCode());
            } catch (Exception compensationFailure) {
                log.error("Tenant provisioning compensation failed for tenant {} at step {}",
                        tenantId, step.stepCode());
            }
        }
    }

    private String readStepStatus(UUID tenantId, String stepCode) {
        return inPlatformTransaction(() -> {
            Object value = entityManager.createNativeQuery(
                            "SELECT status FROM platform.tenant_provision_step "
                                    + "WHERE tenant_id = :tenantId AND step_code = :stepCode")
                    .setParameter("tenantId", tenantId)
                    .setParameter("stepCode", stepCode)
                    .getSingleResult();
            return value.toString();
        });
    }

    private void markRunning(UUID tenantId, UUID actorId, String stepCode) {
        updateStep(tenantId, actorId, stepCode,
                "status = 'RUNNING', attempts = attempts + 1, last_error_code = NULL, "
                        + "started_at = :now, finished_at = NULL");
    }

    private void markDone(UUID tenantId, UUID actorId, String stepCode) {
        updateStep(tenantId, actorId, stepCode,
                "status = 'DONE', last_error_code = NULL, finished_at = :now");
    }

    private void markFailed(UUID tenantId, UUID actorId, String stepCode) {
        updateStep(tenantId, actorId, stepCode,
                "status = 'FAILED', last_error_code = :errorCode, finished_at = :now");
    }

    private void markCompensated(UUID tenantId, UUID actorId, String stepCode) {
        updateStep(tenantId, actorId, stepCode,
                "status = 'COMPENSATED', last_error_code = NULL, finished_at = :now");
    }

    private void updateStep(UUID tenantId, UUID actorId, String stepCode, String assignments) {
        inPlatformTransaction(() -> {
            var query = entityManager.createNativeQuery(
                            "UPDATE platform.tenant_provision_step SET " + assignments
                                    + ", updated_at = :now, updated_by = :actor "
                                    + "WHERE tenant_id = :tenantId AND step_code = :stepCode")
                    .setParameter("now", Instant.now())
                    .setParameter("actor", actorId)
                    .setParameter("tenantId", tenantId)
                    .setParameter("stepCode", stepCode);
            if (assignments.contains(":errorCode")) {
                query.setParameter("errorCode", FAILED_ERROR_CODE);
            }
            int changed = query.executeUpdate();
            if (changed != 1) {
                throw new IllegalStateException("Provisioning step row is missing: " + stepCode);
            }
            return null;
        });
    }

    private <T> T inPlatformTransaction(Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(platformTransactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> work.get());
    }

    private static void requireIds(UUID tenantId, UUID actorId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(actorId, "actorId is required");
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        throw new IllegalStateException("Unsupported provisioning timestamp type: " + value.getClass().getName());
    }

    private record StepDefinition(int executionOrder, String planStep, String stepCode, String description) {
    }

    private record StepState(String status, int attempts, String lastErrorCode,
                             Instant startedAt, Instant finishedAt) {
    }

    /** Work and optional reverse action for one idempotent provisioning step. */
    public interface StepAction {
        void execute() throws Exception;

        default void compensate() throws Exception {
            // No external side effect to reverse.
        }
    }
}
