package com.edu360.api.platformops.entitlement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.edu360.api.exception.ModuleNotEnabledException;
import com.edu360.api.tenant.TenantRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * I-07 (H15): module entitlements actually applied.
 *
 * A module is OFF for a tenant when the tenant has an override row in platform.tenant_entitlement
 * (feature_code NULL) with enabled = false, or - without a tenant override - its plan has a
 * platform.plan_entitlement row with enabled = false. A tenant override always wins over the
 * plan. A module with no row anywhere is ON: entitlement rows are the switches the Product Owner
 * sets (FEATURE_TOGGLE), so an absent switch means nothing was switched off.
 *
 * Which module an action belongs to comes from the database, not from code:
 * ref.action_catalog.module_name -> ref.action_module_map.module_code
 * (sql/24_v41_sign_in_security.sql + 24d). Sign-in and account actions map to no module and are
 * never blocked.
 */
@Service
public class ModuleEntitlementService {

    private static final Logger log = LoggerFactory.getLogger(ModuleEntitlementService.class);
    private static final String NO_MODULE = "";

    private final TenantEntitlementRepository tenantEntitlementRepository;
    private final PlanEntitlementRepository planEntitlementRepository;
    private final TenantRepository tenantRepository;
    private final Map<String, String> moduleByAction = new ConcurrentHashMap<>();

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public ModuleEntitlementService(@Autowired(required = false) TenantEntitlementRepository tenantEntitlementRepository,
                                    @Autowired(required = false) PlanEntitlementRepository planEntitlementRepository,
                                    TenantRepository tenantRepository) {
        this.tenantEntitlementRepository = tenantEntitlementRepository;
        this.planEntitlementRepository = planEntitlementRepository;
        this.tenantRepository = tenantRepository;
    }

    /** For tests: a fixed action-to-module lookup instead of the database. */
    ModuleEntitlementService(TenantEntitlementRepository tenantEntitlementRepository,
                             PlanEntitlementRepository planEntitlementRepository,
                             TenantRepository tenantRepository,
                             Map<String, String> actionModules) {
        this(tenantEntitlementRepository, planEntitlementRepository, tenantRepository);
        this.moduleByAction.putAll(actionModules);
    }

    /** Throws 403 MODULE_NOT_ENABLED when the action's module is switched off for the tenant. */
    public void requireEnabled(UUID tenantId, String actionCode) {
        if (tenantId == null || actionCode == null) {
            return;
        }
        Optional<String> module = moduleOf(actionCode);
        if (module.isPresent() && disabledModules(tenantId).contains(module.get())) {
            throw new ModuleNotEnabledException(module.get());
        }
    }

    /** Module codes switched off for this tenant (sorted). */
    public Set<String> disabledModules(UUID tenantId) {
        Map<String, Boolean> effective = new HashMap<>();
        if (planEntitlementRepository != null) {
            tenantRepository.findById(tenantId).map(t -> t.getPlanId()).ifPresent(planId ->
                    planEntitlementRepository.findByPlanId(planId).stream()
                            .filter(e -> e.getFeatureCode() == null)
                            .forEach(e -> effective.put(e.getModuleCode(), e.isEnabled())));
        }
        if (tenantEntitlementRepository != null) {
            tenantEntitlementRepository.findByTenantId(tenantId).stream()
                    .filter(e -> e.getFeatureCode() == null)
                    .forEach(e -> effective.put(e.getModuleCode(), e.isEnabled()));
        }
        Set<String> disabled = new TreeSet<>();
        effective.forEach((module, enabled) -> {
            if (!enabled) {
                disabled.add(module);
            }
        });
        return disabled;
    }

    /** The entitlement module of an action, if it has one. */
    public Optional<String> moduleOf(String actionCode) {
        String module = moduleByAction.computeIfAbsent(actionCode, this::lookupModule);
        return module.isEmpty() ? Optional.empty() : Optional.of(module);
    }

    private String lookupModule(String actionCode) {
        if (entityManager == null) {
            return NO_MODULE;
        }
        try {
            List<?> rows = entityManager.createNativeQuery(
                            "SELECT m.module_code FROM ref.action_catalog a "
                                    + "JOIN ref.action_module_map m ON m.action_module_name = a.module_name "
                                    + "WHERE a.action_code = :code")
                    .setParameter("code", actionCode)
                    .getResultList();
            return rows.isEmpty() || rows.get(0) == null ? NO_MODULE : rows.get(0).toString();
        } catch (RuntimeException ex) {
            // The H2 test profile has no ref schema: no module is known, so nothing is blocked
            // here - PermissionEvaluatorService still denies unknown actions on its own.
            log.debug("Could not resolve the module of action {}: {}", actionCode, ex.getMessage());
            return NO_MODULE;
        }
    }
}
