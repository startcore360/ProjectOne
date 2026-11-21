package com.edu360.api.platformops.subscription;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platformops.PlatformConcurrency;
import com.edu360.api.platformops.billing.Invoice;
import com.edu360.api.platformops.billing.InvoiceLine;
import com.edu360.api.platformops.billing.InvoiceLineRepository;
import com.edu360.api.platformops.billing.InvoiceRepository;
import com.edu360.api.platformops.entitlement.PlanEntitlement;
import com.edu360.api.platformops.entitlement.PlanEntitlementRepository;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.subscription.dto.AddonDecisionRequest;
import com.edu360.api.platformops.subscription.dto.CommercialCatalogItemRequest;
import com.edu360.api.platformops.subscription.dto.CommercialCatalogItemResponse;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteRequest;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteResponse;
import com.edu360.api.platformops.subscription.dto.PlanComponentRequest;
import com.edu360.api.platformops.subscription.dto.PlanComponentResponse;
import com.edu360.api.platformops.subscription.dto.TenantAddonRequest;
import com.edu360.api.platformops.subscription.dto.TenantAddonResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantState;
import com.edu360.api.tenant.TenantRepository;

@Service
public class PlatformSubscriptionService {
    private static final Set<String> CATEGORIES = Set.of("CAPACITY", "MODULE", "SERVICE");
    private static final Set<String> PRICING_BASES = Set.of(
            "ONE_TIME", "RECURRING_FIXED", "PER_UNIT", "PER_STUDENT_YEAR", "PER_MODULE_YEAR", "USAGE");
    private static final Set<String> CAPACITY_UNITS = Set.of(
            "INSTITUTION", "COLLEGE", "SCHOOL", "STUDENT_PACK", "STAFF_PACK");
    private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Kolkata");

    private final CommercialCatalogItemRepository catalogRepository;
    private final PlanComponentRepository componentRepository;
    private final TenantSubscriptionAddonRepository addonRepository;
    private final PlanRepository planRepository;
    private final TenantRepository tenantRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final PlanEntitlementRepository planEntitlementRepository;
    private final PlatformUserRepository platformUserRepository;
    private final SubscriptionQuoteCalculator quoteCalculator;
    private final PlatformAuditWriter auditWriter;

    public PlatformSubscriptionService(CommercialCatalogItemRepository catalogRepository,
            PlanComponentRepository componentRepository, TenantSubscriptionAddonRepository addonRepository,
            PlanRepository planRepository, TenantRepository tenantRepository,
            InvoiceRepository invoiceRepository, InvoiceLineRepository invoiceLineRepository,
            PlanEntitlementRepository planEntitlementRepository, PlatformUserRepository platformUserRepository,
            SubscriptionQuoteCalculator quoteCalculator, PlatformAuditWriter auditWriter) {
        this.catalogRepository = catalogRepository;
        this.componentRepository = componentRepository;
        this.addonRepository = addonRepository;
        this.planRepository = planRepository;
        this.tenantRepository = tenantRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.planEntitlementRepository = planEntitlementRepository;
        this.platformUserRepository = platformUserRepository;
        this.quoteCalculator = quoteCalculator;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public List<CommercialCatalogItemResponse> listCatalog(boolean includeUnavailable) {
        List<CommercialCatalogItem> items = includeUnavailable
                ? catalogRepository.findByOrderByItemNameAsc()
                : catalogRepository.findByAvailableTrueOrderByItemNameAsc();
        return items.stream().map(CommercialCatalogItemResponse::from).toList();
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public CommercialCatalogItemResponse createCatalogItem(CommercialCatalogItemRequest request,
            PlatformPrincipal actor) {
        validateCatalogItem(request);
        String code = request.itemCode().trim().toUpperCase();
        if (catalogRepository.findByItemCode(code).isPresent()) {
            throw new DuplicateResourceException("A commercial item with code '" + code + "' already exists.");
        }
        requirePricePermission(actor, request.unitAmount());
        Instant now = Instant.now();
        CommercialCatalogItem item = CommercialCatalogItem.builder()
                .catalogItemId(UUID.randomUUID())
                .itemCode(code)
                .itemName(request.itemName().trim())
                .category(request.category().trim().toUpperCase())
                .unitCode(request.unitCode().trim().toUpperCase())
                .pricingBasis(request.pricingBasis().trim().toUpperCase())
                .unitAmount(actor.platformRole() == PlatformRole.PRODUCT_OWNER
                        ? request.unitAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2))
                .unitsPerQuantity(request.unitsPerQuantity())
                .billingIntervalMonths(request.billingIntervalMonths())
                .moduleCode(normalizeModule(request.moduleCode()))
                .description(trimToNull(request.description()))
                .available(request.available() == null || request.available())
                .createdAt(now).createdBy(actor.platformUserId())
                .updatedAt(now).updatedBy(actor.platformUserId()).rowVersion(1)
                .build();
        CommercialCatalogItem saved = catalogRepository.saveAndFlush(item);
        auditWriter.record(actor, "COMMERCIAL_ITEM_CREATED", "PLAN_MANAGE", "COMMERCIAL_CATALOG_ITEM", saved.getCatalogItemId());
        return CommercialCatalogItemResponse.from(saved);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public CommercialCatalogItemResponse updateCatalogItem(UUID itemId, CommercialCatalogItemRequest request,
            PlatformPrincipal actor, String ifMatch) {
        validateCatalogItem(request);
        CommercialCatalogItem item = catalogRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Commercial item not found: " + itemId));
        new PlatformConcurrency().requireIfMatch(ifMatch, item.getRowVersion());
        if (!item.getItemCode().equalsIgnoreCase(request.itemCode().trim())
                || !item.getCategory().equalsIgnoreCase(request.category().trim())
                || !item.getUnitCode().equalsIgnoreCase(request.unitCode().trim())
                || !java.util.Objects.equals(item.getModuleCode(), normalizeModule(request.moduleCode()))
                || !item.getUnitsPerQuantity().equals(request.unitsPerQuantity())) {
            throw new ApiException(HttpStatus.CONFLICT, "COMMERCIAL_ITEM_IDENTITY_IMMUTABLE",
                    "Item code, category, unit, module and capacity pack size cannot change after creation.");
        }
        boolean priceChanged = request.unitAmount().compareTo(item.getUnitAmount()) != 0
                || !item.getPricingBasis().equalsIgnoreCase(request.pricingBasis().trim())
                || !java.util.Objects.equals(item.getBillingIntervalMonths(), request.billingIntervalMonths());
        if (actor.platformRole() != PlatformRole.PRODUCT_OWNER && priceChanged) {
            throw new com.edu360.api.exception.UnauthorizedException(
                    "Only the Product Owner can change commercial item pricing.");
        }
        item.setItemName(request.itemName().trim());
        item.setPricingBasis(request.pricingBasis().trim().toUpperCase());
        item.setBillingIntervalMonths(request.billingIntervalMonths());
        item.setDescription(trimToNull(request.description()));
        if (request.available() != null) item.setAvailable(request.available());
        if (actor.platformRole() == PlatformRole.PRODUCT_OWNER) {
            item.setUnitAmount(request.unitAmount().setScale(2, RoundingMode.HALF_UP));
        }
        item.setUpdatedAt(Instant.now());
        item.setUpdatedBy(actor.platformUserId());
        item.setRowVersion(item.getRowVersion() + 1);
        CommercialCatalogItem saved = catalogRepository.saveAndFlush(item);
        auditWriter.record(actor, "COMMERCIAL_ITEM_UPDATED", "PLAN_MANAGE", "COMMERCIAL_CATALOG_ITEM", saved.getCatalogItemId());
        return CommercialCatalogItemResponse.from(saved);
    }

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public List<PlanComponentResponse> listPlanComponents(UUID planId) {
        requirePlan(planId);
        Map<UUID, CommercialCatalogItem> items = catalogRepository.findAll().stream()
                .collect(Collectors.toMap(CommercialCatalogItem::getCatalogItemId, Function.identity()));
        return componentRepository.findByPlanIdOrderByPlanComponentId(planId).stream()
                .map(component -> componentResponse(component, items.get(component.getCatalogItemId())))
                .toList();
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public List<PlanComponentResponse> replacePlanComponents(UUID planId, List<PlanComponentRequest> requests,
            PlatformPrincipal actor, String ifMatch) {
        requireProductOwner(actor);
        Plan plan = requirePlan(planId);
        new PlatformConcurrency().requireIfMatch(ifMatch, plan.getRowVersion());
        Set<UUID> itemIds = new HashSet<>();
        List<CommercialCatalogItem> items = new ArrayList<>();
        for (PlanComponentRequest request : requests) {
            if (!itemIds.add(request.catalogItemId())) {
                throw new BusinessRuleViolationException("DUPLICATE_PLAN_COMPONENT",
                        "A commercial item can only appear once in a plan.");
            }
            CommercialCatalogItem item = catalogRepository.findById(request.catalogItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Commercial item not found: " + request.catalogItemId()));
            if (!item.isAvailable()) {
                throw new BusinessRuleViolationException("COMMERCIAL_ITEM_UNAVAILABLE",
                        "Unavailable items cannot be added to a plan.");
            }
            if ("CAPACITY".equals(item.getCategory())
                    && request.includedQuantity().stripTrailingZeros().scale() > 0) {
                throw new BusinessRuleViolationException("CAPACITY_QUANTITY_MUST_BE_WHOLE",
                        "Capacity package quantities must be whole numbers.");
            }
            if ("MODULE".equals(item.getCategory())
                    && request.includedQuantity().compareTo(BigDecimal.ONE) != 0) {
                throw new BusinessRuleViolationException("MODULE_QUANTITY_INVALID",
                        "A module component quantity must be one.");
            }
            items.add(item);
        }
        componentRepository.deleteByPlanId(planId);
        Instant now = Instant.now();
        List<PlanComponent> savedComponents = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            PlanComponentRequest request = requests.get(i);
            savedComponents.add(PlanComponent.builder()
                    .planComponentId(UUID.randomUUID()).planId(planId).catalogItemId(request.catalogItemId())
                    .includedQuantity(request.includedQuantity().setScale(2, RoundingMode.HALF_UP))
                    .unitAmountOverride(request.unitAmountOverride() == null ? null
                            : request.unitAmountOverride().setScale(2, RoundingMode.HALF_UP))
                    .enabled(request.enabled() == null || request.enabled())
                    .createdAt(now).createdBy(actor.platformUserId()).updatedAt(now)
                    .updatedBy(actor.platformUserId()).rowVersion(1).build());
        }
        componentRepository.saveAll(savedComponents);
        plan.setUpdatedAt(now);
        plan.setUpdatedBy(actor.platformUserId());
        plan.setRowVersion(plan.getRowVersion() + 1);
        planRepository.save(plan);
        auditWriter.record(actor, "PLAN_COMPONENTS_UPDATED", "PLAN_MANAGE", "PLAN", planId);
        Map<UUID, CommercialCatalogItem> itemById = items.stream()
                .collect(Collectors.toMap(CommercialCatalogItem::getCatalogItemId, Function.identity()));
        return savedComponents.stream().map(c -> componentResponse(c, itemById.get(c.getCatalogItemId()))).toList();
    }

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public CustomerQuoteResponse quote(CustomerQuoteRequest request) {
        List<Plan> plans = planRepository.findByStatusOrderByPlanNameAsc("ACTIVE");
        Map<UUID, List<PlanComponent>> components = new HashMap<>();
        Map<UUID, List<PlanEntitlement>> entitlements = new HashMap<>();
        for (Plan plan : plans) {
            components.put(plan.getPlanId(), componentRepository.findByPlanIdAndEnabledTrueOrderByPlanComponentId(plan.getPlanId()));
            entitlements.put(plan.getPlanId(), planEntitlementRepository.findByPlanId(plan.getPlanId()));
        }
        return quoteCalculator.quote(request, plans, components, catalogRepository.findByOrderByItemNameAsc(), entitlements);
    }

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public List<TenantAddonResponse> listAddonRequests(String status) {
        List<TenantSubscriptionAddon> requests = status == null || status.isBlank()
                ? addonRepository.findAllByOrderByCreatedAtDesc()
                : addonRepository.findByStatusOrderByCreatedAtAsc(status.trim().toUpperCase());
        Map<UUID, CommercialCatalogItem> items = catalogRepository.findAll().stream()
                .collect(Collectors.toMap(CommercialCatalogItem::getCatalogItemId, Function.identity()));
        return requests.stream().map(request -> addonResponse(request, items.get(request.getCatalogItemId()))).toList();
    }

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public List<TenantAddonResponse> listTenantAddonRequests(UUID tenantId) {
        Map<UUID, CommercialCatalogItem> items = catalogRepository.findAll().stream()
                .collect(Collectors.toMap(CommercialCatalogItem::getCatalogItemId, Function.identity()));
        return addonRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(request -> addonResponse(request, items.get(request.getCatalogItemId()))).toList();
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public TenantAddonResponse requestAddon(UUID tenantId, UUID requesterId, TenantAddonRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
        if (tenant.getState() != TenantState.ACTIVE && tenant.getState() != TenantState.TRIAL) {
            throw new ApiException(HttpStatus.CONFLICT, "TENANT_NOT_ACTIVE",
                    "Add-on requests are available only for active or trial tenants.");
        }
        CommercialCatalogItem item = catalogRepository.findById(request.catalogItemId())
                .filter(CommercialCatalogItem::isAvailable)
                .orElseThrow(() -> new ResourceNotFoundException("Available commercial item not found."));
        if ("USAGE".equals(item.getPricingBasis())) {
            throw new BusinessRuleViolationException("USAGE_ADDON_UNAVAILABLE",
                    "Usage-based add-ons are not available while SMS/storage charging is under review.");
        }
        if ("CAPACITY".equals(item.getCategory())
                && request.quantity().stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleViolationException("CAPACITY_QUANTITY_MUST_BE_WHOLE",
                    "Capacity package quantities must be whole numbers.");
        }
        Instant now = Instant.now();
        TenantSubscriptionAddon addon = TenantSubscriptionAddon.builder()
                .subscriptionAddonId(UUID.randomUUID()).tenantId(tenantId).catalogItemId(item.getCatalogItemId())
                .quantity(request.quantity().setScale(2, RoundingMode.HALF_UP))
                .unitAmount(item.getUnitAmount()).status("REQUESTED").requestedByUserId(requesterId)
                .requestReason(trimToNull(request.reason())).createdAt(now).createdBy(requesterId)
                .updatedAt(now).updatedBy(requesterId).rowVersion(1).build();
        return addonResponse(addonRepository.saveAndFlush(addon), item);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public TenantAddonResponse approveAddon(UUID addonId, AddonDecisionRequest request, PlatformPrincipal actor) {
        requireProductOwner(actor);
        TenantSubscriptionAddon addon = requireAddon(addonId);
        requireStatus(addon, "REQUESTED");
        if (request != null && request.unitAmount() != null) {
            addon.setUnitAmount(request.unitAmount().setScale(2, RoundingMode.HALF_UP));
        }
        addon.setStatus("APPROVED");
        addon.setApprovedByPlatformUserId(actor.platformUserId());
        addon.setApprovedAt(Instant.now());
        addon.setDecisionReason(request == null ? null : trimToNull(request.reason()));
        addon.setUpdatedAt(Instant.now());
        addon.setUpdatedBy(actor.platformUserId());
        TenantSubscriptionAddon saved = addonRepository.saveAndFlush(addon);
        auditWriter.record(actor, addon.getTenantId(), "TENANT_ADDON_APPROVED", "APPROVE",
                "PLAN_MANAGE", "TENANT_SUBSCRIPTION_ADDON", saved.getSubscriptionAddonId(),
                PlatformAuditWriter.SUCCESS);
        return addonResponse(saved, catalogRepository.findById(saved.getCatalogItemId()).orElse(null));
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public TenantAddonResponse rejectAddon(UUID addonId, AddonDecisionRequest request, PlatformPrincipal actor) {
        requireProductOwner(actor);
        TenantSubscriptionAddon addon = requireAddon(addonId);
        requireStatus(addon, "REQUESTED");
        addon.setStatus("REJECTED");
        addon.setApprovedByPlatformUserId(actor.platformUserId());
        addon.setApprovedAt(Instant.now());
        addon.setDecisionReason(request == null ? null : trimToNull(request.reason()));
        addon.setUpdatedAt(Instant.now());
        addon.setUpdatedBy(actor.platformUserId());
        TenantSubscriptionAddon saved = addonRepository.saveAndFlush(addon);
        auditWriter.record(actor, addon.getTenantId(), "TENANT_ADDON_REJECTED", "REJECT",
                "PLAN_MANAGE", "TENANT_SUBSCRIPTION_ADDON", saved.getSubscriptionAddonId(),
                PlatformAuditWriter.SUCCESS);
        return addonResponse(saved, catalogRepository.findById(saved.getCatalogItemId()).orElse(null));
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public TenantAddonResponse invoiceAddon(UUID addonId, PlatformPrincipal actor) {
        if (actor.platformRole() != PlatformRole.BILLING_MANAGER) {
            throw new com.edu360.api.exception.UnauthorizedException("Only a Billing Manager may invoice an approved add-on.");
        }
        TenantSubscriptionAddon addon = requireAddon(addonId);
        requireStatus(addon, "APPROVED");
        CommercialCatalogItem item = catalogRepository.findById(addon.getCatalogItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Commercial item not found."));
        LocalDate start = LocalDate.now(BILLING_ZONE);
        int interval = item.getBillingIntervalMonths() == null ? 1 : item.getBillingIntervalMonths();
        LocalDate end = "ONE_TIME".equals(item.getPricingBasis()) ? start : start.plusMonths(interval).minusDays(1);
        BigDecimal billedQuantity = addon.getQuantity();
        if ("PER_STUDENT_YEAR".equals(item.getPricingBasis()) || "PER_MODULE_YEAR".equals(item.getPricingBasis())) {
            billedQuantity = billedQuantity.multiply(BigDecimal.valueOf(interval))
                    .divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
        }
        BigDecimal amount = billedQuantity.multiply(addon.getUnitAmount()).setScale(2, RoundingMode.HALF_UP);
        Instant now = Instant.now();
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder().invoiceId(invoiceId).tenantId(addon.getTenantId())
                .invoiceNumber("INV-" + start.getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .periodStart(start).periodEnd(end).subtotalAmount(amount).discountAmount(BigDecimal.ZERO.setScale(2))
                .totalAmount(amount).currencyCode("INR").status("DRAFT")
                .createdAt(now).createdBy(actor.platformUserId()).updatedAt(now).updatedBy(actor.platformUserId())
                .rowVersion(1).build();
        invoiceRepository.saveAndFlush(invoice);
        InvoiceLine line = InvoiceLine.builder().invoiceLineId(UUID.randomUUID()).invoiceId(invoiceId)
                .lineType("ADDON").description(item.getItemName()).quantity(billedQuantity.setScale(6))
                .unitAmount(addon.getUnitAmount()).lineAmount(amount).catalogItemId(item.getCatalogItemId())
                .subscriptionAddonId(addon.getSubscriptionAddonId())
                .createdAt(now).createdBy(actor.platformUserId()).updatedAt(now)
                .updatedBy(actor.platformUserId()).rowVersion(1).build();
        invoiceLineRepository.saveAndFlush(line);
        addon.setInvoiceId(invoiceId);
        addon.setStatus("INVOICED");
        addon.setUpdatedAt(now);
        addon.setUpdatedBy(actor.platformUserId());
        TenantSubscriptionAddon saved = addonRepository.saveAndFlush(addon);
        auditWriter.record(actor, addon.getTenantId(), "TENANT_ADDON_INVOICED", "CREATE",
                "INVOICE_MANAGE", "INVOICE", invoiceId, PlatformAuditWriter.SUCCESS);
        return addonResponse(saved, item);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public void activatePaidAddons(UUID invoiceId, PlatformPrincipal actor, Instant paidAt) {
        List<TenantSubscriptionAddon> pending = addonRepository.findByInvoiceId(invoiceId);
        LocalDate start = paidAt.atZone(BILLING_ZONE).toLocalDate();
        for (TenantSubscriptionAddon addon : pending) {
            if (!"INVOICED".equals(addon.getStatus())) continue;
            CommercialCatalogItem item = catalogRepository.findById(addon.getCatalogItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Commercial item not found for paid add-on."));
            addon.setStatus("ACTIVE");
            addon.setEffectiveFrom(start);
            addon.setEffectiveTo("ONE_TIME".equals(item.getPricingBasis()) ? null
                    : start.plusMonths(item.getBillingIntervalMonths() == null ? 1 : item.getBillingIntervalMonths()).minusDays(1));
            addon.setUpdatedAt(Instant.now());
            addon.setUpdatedBy(actor.platformUserId());
            addonRepository.save(addon);
            auditWriter.record(actor, addon.getTenantId(), "TENANT_ADDON_ACTIVATED", "ACTIVATE",
                    "INVOICE_MANAGE", "TENANT_SUBSCRIPTION_ADDON", addon.getSubscriptionAddonId(),
                    PlatformAuditWriter.SUCCESS);
        }
    }

    private Plan requirePlan(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));
    }

    private TenantSubscriptionAddon requireAddon(UUID addonId) {
        return addonRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("Add-on request not found: " + addonId));
    }

    private static void requireStatus(TenantSubscriptionAddon addon, String expected) {
        if (!expected.equals(addon.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ADDON_STATUS_CONFLICT",
                    "This add-on is " + addon.getStatus() + "; expected " + expected + ".");
        }
    }

    private static void requirePricePermission(PlatformPrincipal actor, BigDecimal amount) {
        if (actor.platformRole() != PlatformRole.PRODUCT_OWNER && amount.signum() != 0) {
            throw new com.edu360.api.exception.UnauthorizedException(
                    "Only the Product Owner can set a commercial item price.");
        }
    }

    private static void requireProductOwner(PlatformPrincipal actor) {
        if (actor.platformRole() != PlatformRole.PRODUCT_OWNER) {
            throw new com.edu360.api.exception.UnauthorizedException("Only the Product Owner may approve commercial changes.");
        }
    }

    private static void validateCatalogItem(CommercialCatalogItemRequest request) {
        String category = request.category().trim().toUpperCase();
        String unitCode = request.unitCode().trim().toUpperCase();
        String basis = request.pricingBasis().trim().toUpperCase();
        if (!CATEGORIES.contains(category) || !PRICING_BASES.contains(basis)) {
            throw new BusinessRuleViolationException("INVALID_COMMERCIAL_ITEM",
                    "Commercial item category or pricing basis is not supported.");
        }
        if ("CAPACITY".equals(category) && !CAPACITY_UNITS.contains(unitCode)) {
            throw new BusinessRuleViolationException("INVALID_CAPACITY_UNIT", "Capacity items need a supported capacity unit.");
        }
        if ("MODULE".equals(category) && (!"MODULE".equals(unitCode) || normalizeModule(request.moduleCode()) == null)) {
            throw new BusinessRuleViolationException("INVALID_MODULE_ITEM", "Module items require a module code and MODULE unit.");
        }
        if ("SERVICE".equals(category) && !"SERVICE".equals(unitCode)) {
            throw new BusinessRuleViolationException("INVALID_SERVICE_UNIT", "Service items need the SERVICE unit.");
        }
        if ("USAGE".equals(basis)) {
            throw new BusinessRuleViolationException("USAGE_ADDON_UNAVAILABLE",
                    "Usage-based charging is deferred while SMS/storage policy is unresolved.");
        }
        if (!"ONE_TIME".equals(basis) && request.billingIntervalMonths() == null) {
            throw new BusinessRuleViolationException("BILLING_INTERVAL_REQUIRED",
                    "Recurring commercial items need a billing interval from 1 to 36 months.");
        }
        if ("CAPACITY".equals(category) && !Set.of("ONE_TIME", "RECURRING_FIXED", "PER_UNIT").contains(basis)) {
            throw new BusinessRuleViolationException("INVALID_CAPACITY_PRICING",
                    "Capacity packages must use one-time or recurring per-unit pricing.");
        }
    }

    private PlanComponentResponse componentResponse(PlanComponent component, CommercialCatalogItem item) {
        if (item == null) {
            throw new IllegalStateException("Plan component references a missing commercial item.");
        }
        BigDecimal amount = component.getUnitAmountOverride() == null
                ? item.getUnitAmount() : component.getUnitAmountOverride();
        return new PlanComponentResponse(component.getPlanComponentId(), item.getCatalogItemId(), item.getItemCode(),
                item.getItemName(), item.getCategory(), item.getUnitCode(), item.getPricingBasis(),
                component.getIncludedQuantity(), amount, component.isEnabled());
    }

    private TenantAddonResponse addonResponse(TenantSubscriptionAddon addon, CommercialCatalogItem item) {
        if (item == null) {
            throw new IllegalStateException("Add-on request references a missing commercial item.");
        }
        return TenantAddonResponse.from(addon, item.getItemCode(), item.getItemName());
    }

    private static String normalizeModule(String moduleCode) {
        return moduleCode == null || moduleCode.isBlank() ? null : moduleCode.trim().toUpperCase();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
