package com.edu360.api.platformops.subscription;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.edu360.api.platform.Plan;
import com.edu360.api.platformops.entitlement.PlanEntitlement;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteRequest;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteResponse;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteResponse.PlanQuote;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteResponse.QuoteLine;

@Component
public class SubscriptionQuoteCalculator {
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public CustomerQuoteResponse quote(CustomerQuoteRequest request, List<Plan> plans,
            Map<UUID, List<PlanComponent>> componentsByPlan, List<CommercialCatalogItem> catalog,
            Map<UUID, List<PlanEntitlement>> entitlementsByPlan) {
        Map<UUID, CommercialCatalogItem> items = catalog.stream()
                .collect(Collectors.toMap(CommercialCatalogItem::getCatalogItemId, Function.identity()));
        List<Candidate> candidates = plans.stream()
                .map(plan -> candidate(plan, request, componentsByPlan.getOrDefault(plan.getPlanId(), List.of()),
                        items, catalog, entitlementsByPlan.getOrDefault(plan.getPlanId(), List.of())))
                .toList();

        Candidate lowest = candidates.stream().filter(c -> c.quote().eligible())
                .min(Comparator.comparing((Candidate c) -> c.quote().total())
                        .thenComparing(c -> c.quote().planName()))
                .orElse(null);
        Candidate best = candidates.stream().filter(c -> c.quote().eligible())
                .min(Comparator.comparingLong(Candidate::fitWaste)
                        .thenComparing(c -> c.quote().total())
                        .thenComparing(c -> c.quote().planName()))
                .orElse(null);

        List<PlanQuote> quotes = candidates.stream()
                .map(c -> {
                    String recommendation = c == lowest && c == best ? "LOWEST_COST_AND_BEST_FIT"
                            : c == lowest ? "LOWEST_COST" : c == best ? "BEST_FIT" : null;
                    PlanQuote q = c.quote();
                    return new PlanQuote(q.planId(), q.planName(), q.pricingType(), q.eligible(), recommendation,
                            q.fitReasons(), q.unmetRequirements(), q.subtotal(), q.discount(), q.total(), q.tax(), q.lines());
                })
                .sorted(Comparator.comparing(PlanQuote::eligible).reversed()
                        .thenComparing(PlanQuote::total)
                        .thenComparing(PlanQuote::planName))
                .toList();

        return new CustomerQuoteResponse("INR", request.billingCycleMonths(), quotes,
                lowest == null ? null : lowest.quote().planId(),
                best == null ? null : best.quote().planId());
    }

    private Candidate candidate(Plan plan, CustomerQuoteRequest request, List<PlanComponent> components,
            Map<UUID, CommercialCatalogItem> itemById, List<CommercialCatalogItem> catalog,
            List<PlanEntitlement> entitlements) {
        List<QuoteLine> lines = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        List<String> unmet = new ArrayList<>();
        Set<String> modules = new HashSet<>();

        entitlements.stream().filter(PlanEntitlement::isEnabled)
                .map(PlanEntitlement::getModuleCode).forEach(modules::add);
        components.stream().filter(PlanComponent::isEnabled)
                .map(PlanComponent::getCatalogItemId).map(itemById::get)
                .filter(item -> item != null && "MODULE".equals(item.getCategory()))
                .map(CommercialCatalogItem::getModuleCode).filter(code -> code != null)
                .forEach(modules::add);

        BigDecimal collegeCapacity = capacity(plan.getMaxColleges(), components, itemById, "COLLEGE");
        BigDecimal schoolCapacity = capacity(plan.getMaxSchools(), components, itemById, "SCHOOL");
        BigDecimal studentCapacity = capacity(plan.getMaxStudents(), components, itemById, "STUDENT_PACK");
        BigDecimal staffCapacity = capacity(plan.getMaxStaff(), components, itemById, "STAFF_PACK");
        BigDecimal institutionCapacity = capacity(plan.getMaxInstitutions(), components, itemById, "INSTITUTION");
        if (institutionCapacity != null) {
            institutionCapacity = institutionCapacity
                    .add(componentCapacity(components, itemById, "COLLEGE"))
                    .add(componentCapacity(components, itemById, "SCHOOL"));
        }

        List<QuoteLine> addOnLines = new ArrayList<>();
        collegeCapacity = addCapacityIfNeeded("COLLEGE", request.colleges(), collegeCapacity,
                request.billingCycleMonths(), catalog, addOnLines, unmet);
        schoolCapacity = addCapacityIfNeeded("SCHOOL", request.schools(), schoolCapacity,
                request.billingCycleMonths(), catalog, addOnLines, unmet);
        studentCapacity = addCapacityIfNeeded("STUDENT_PACK", request.students(), studentCapacity,
                request.billingCycleMonths(), catalog, addOnLines, unmet);
        staffCapacity = addCapacityIfNeeded("STAFF_PACK", request.staff(), staffCapacity,
                request.billingCycleMonths(), catalog, addOnLines, unmet);

        if (exceeds(request.colleges() + request.schools(), institutionCapacity)) {
            institutionCapacity = addCapacityIfNeeded("INSTITUTION", request.colleges() + request.schools(),
                    institutionCapacity, request.billingCycleMonths(), catalog, addOnLines, unmet);
        }

        if (exceeds(request.colleges(), collegeCapacity)) unmet.add("College capacity");
        if (exceeds(request.schools(), schoolCapacity)) unmet.add("School capacity");
        if (exceeds(request.students(), studentCapacity)) unmet.add("Student capacity");
        if (exceeds(request.staff(), staffCapacity)) unmet.add("Staff capacity");
        if (exceeds(request.colleges() + request.schools(), institutionCapacity)) unmet.add("Combined institution capacity");

        if (collegeCapacity != null && collegeCapacity.compareTo(BigDecimal.valueOf(request.colleges())) >= 0)
            reasons.add("College capacity covers the request");
        if (schoolCapacity != null && schoolCapacity.compareTo(BigDecimal.valueOf(request.schools())) >= 0)
            reasons.add("School capacity covers the request");
        if (studentCapacity != null && studentCapacity.compareTo(BigDecimal.valueOf(request.students())) >= 0)
            reasons.add("Student capacity covers the request");
        if (staffCapacity != null && staffCapacity.compareTo(BigDecimal.valueOf(request.staff())) >= 0)
            reasons.add("Staff capacity covers the request");

        Set<String> requestedModules = request.requiredModules() == null ? Set.of()
                : request.requiredModules().stream().map(String::trim).map(String::toUpperCase)
                        .filter(code -> !code.isBlank()).collect(Collectors.toSet());
        for (String module : requestedModules) {
            if (modules.contains(module)) {
                reasons.add(module + " module is included");
                continue;
            }
            CommercialCatalogItem moduleAddon = catalog.stream()
                    .filter(CommercialCatalogItem::isAvailable)
                    .filter(item -> "MODULE".equals(item.getCategory()) && module.equals(item.getModuleCode()))
                    .min(Comparator.comparing(CommercialCatalogItem::getUnitAmount)).orElse(null);
            if (moduleAddon == null) {
                unmet.add("Module " + module);
            } else {
                addOnLines.add(priceLine(moduleAddon, BigDecimal.ONE, request.billingCycleMonths()));
                reasons.add(module + " is available as a priced add-on");
            }
        }
        long includedRequestedModules = requestedModules.stream().filter(modules::contains).count();

        String pricingType = plan.getPricingType() == null ? "FLAT" : plan.getPricingType();
        BigDecimal months = BigDecimal.valueOf(request.billingCycleMonths());
        boolean legacy = !"V2".equals(plan.getPricingEngineVersion());
        if (!legacy && "PER_MODULE".equals(pricingType) && requestedModules.isEmpty()) {
            unmet.add("Select at least one required module to price this plan");
        }
        if (legacy) {
            lines.add(line("PLAN", "Legacy plan fee", months, money(plan.getPriceAmount()), months.multiply(money(plan.getPriceAmount()))));
        } else if ("PER_STUDENT_YEAR".equals(pricingType)) {
            BigDecimal studentYears = BigDecimal.valueOf(request.students()).multiply(months)
                    .divide(TWELVE, 6, RoundingMode.HALF_UP);
            lines.add(line("PLAN", "Student-year subscription", studentYears, money(plan.getPriceAmount()),
                    studentYears.multiply(money(plan.getPriceAmount()))));
        } else if ("FLAT".equals(pricingType)) {
            lines.add(line("PLAN", "Flat subscription", months, money(plan.getPriceAmount()),
                    months.multiply(money(plan.getPriceAmount()))));
        } else if ("PER_MODULE".equals(pricingType)) {
            BigDecimal moduleYears = BigDecimal.valueOf(includedRequestedModules).multiply(months)
                    .divide(TWELVE, 6, RoundingMode.HALF_UP);
            lines.add(line("MODULE", "Selected modules", moduleYears, money(plan.getPriceAmount()),
                    moduleYears.multiply(money(plan.getPriceAmount()))));
        } else {
            lines.add(line("PLAN", "Package base", months, money(plan.getPriceAmount()),
                    months.multiply(money(plan.getPriceAmount()))));
        }

        if (request.includeSetupFee() && plan.getSetupFeeAmount() != null
                && plan.getSetupFeeAmount().signum() > 0) {
            lines.add(line("SETUP", "One-time setup fee", BigDecimal.ONE, money(plan.getSetupFeeAmount()),
                    plan.getSetupFeeAmount()));
        }
        if (!legacy) {
            BigDecimal studentYears = BigDecimal.valueOf(request.students()).multiply(months)
                    .divide(TWELVE, 6, RoundingMode.HALF_UP);
            for (PlanComponent component : components) {
                if (!component.isEnabled()) continue;
                CommercialCatalogItem item = itemById.get(component.getCatalogItemId());
                if (item == null || "USAGE".equals(item.getPricingBasis())) continue;
                if ("PER_MODULE".equals(pricingType) && "MODULE".equals(item.getCategory())) continue;
                BigDecimal unit = component.getUnitAmountOverride() == null
                        ? item.getUnitAmount() : component.getUnitAmountOverride();
                QuoteLine componentLine = componentLine(item, component.getIncludedQuantity(), unit,
                        studentYears, request.billingCycleMonths());
                if (componentLine != null) lines.add(componentLine);
            }
        }
        lines.addAll(addOnLines);

        BigDecimal subtotal = lines.stream().map(QuoteLine::amount)
                .reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = legacy ? ZERO : discount(subtotal, plan);
        BigDecimal total = subtotal.subtract(discount).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        boolean eligible = unmet.isEmpty();
        if (eligible && reasons.isEmpty()) reasons.add("Plan meets the entered requirements");
        long waste = capacityWaste(collegeCapacity, request.colleges())
                + capacityWaste(schoolCapacity, request.schools())
                + capacityWaste(studentCapacity, request.students())
                + capacityWaste(staffCapacity, request.staff())
                + Math.max(0, modules.size() - requestedModules.size());
        return new Candidate(new PlanQuote(plan.getPlanId(), plan.getPlanName(), pricingType, eligible, null,
                reasons, List.copyOf(new LinkedHashSet<>(unmet)), subtotal, discount, total, ZERO, lines), waste);
    }

    private BigDecimal addCapacityIfNeeded(String unitCode, int requested, BigDecimal current, int months,
            List<CommercialCatalogItem> catalog, List<QuoteLine> lines, List<String> unmet) {
        if (current == null || current.compareTo(BigDecimal.valueOf(requested)) >= 0) return current;
        int missing = BigDecimal.valueOf(requested).subtract(current).intValueExact();
        CommercialCatalogItem pack = catalog.stream()
                .filter(CommercialCatalogItem::isAvailable)
                .filter(item -> "CAPACITY".equals(item.getCategory()) && unitCode.equals(item.getUnitCode()))
                .filter(item -> !"USAGE".equals(item.getPricingBasis()))
                .min(Comparator.comparing(item -> item.getUnitAmount()
                        .divide(BigDecimal.valueOf(item.getUnitsPerQuantity()), 6, RoundingMode.HALF_UP)))
                .orElse(null);
        if (pack == null) {
            unmet.add(unitCode + " capacity");
            return current;
        }
        int quantity = (missing + pack.getUnitsPerQuantity() - 1) / pack.getUnitsPerQuantity();
        BigDecimal qty = BigDecimal.valueOf(quantity);
        lines.add(priceLine(pack, qty, months));
        return current.add(qty.multiply(BigDecimal.valueOf(pack.getUnitsPerQuantity())));
    }

    private BigDecimal capacity(Integer base, List<PlanComponent> components,
            Map<UUID, CommercialCatalogItem> itemById, String unitCode) {
        if (base == null) return null;
        return BigDecimal.valueOf(base).add(componentCapacity(components, itemById, unitCode));
    }

    private BigDecimal componentCapacity(List<PlanComponent> components,
            Map<UUID, CommercialCatalogItem> itemById, String unitCode) {
        return components.stream().filter(PlanComponent::isEnabled)
                .map(component -> Map.entry(component, itemById.get(component.getCatalogItemId())))
                .filter(entry -> entry.getValue() != null && "CAPACITY".equals(entry.getValue().getCategory())
                        && unitCode.equals(entry.getValue().getUnitCode()))
                .map(entry -> entry.getKey().getIncludedQuantity()
                        .multiply(BigDecimal.valueOf(entry.getValue().getUnitsPerQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<QuoteLine> priceComponents(List<PlanComponent> components,
            Map<UUID, CommercialCatalogItem> itemById, BigDecimal studentYears, int months) {
        return components.stream().filter(PlanComponent::isEnabled)
                .map(component -> {
                    CommercialCatalogItem item = itemById.get(component.getCatalogItemId());
                    if (item == null || "USAGE".equals(item.getPricingBasis())) return null;
                    BigDecimal unit = component.getUnitAmountOverride() == null
                            ? item.getUnitAmount() : component.getUnitAmountOverride();
                    return componentLine(item, component.getIncludedQuantity(), unit, studentYears, months);
                })
                .filter(java.util.Objects::nonNull).toList();
    }

    private QuoteLine componentLine(CommercialCatalogItem item, BigDecimal quantity, BigDecimal unitAmount,
            BigDecimal studentYears, int months) {
        if (quantity == null || quantity.signum() <= 0) return null;
        BigDecimal q;
        BigDecimal unit = money(unitAmount);
        switch (item.getPricingBasis()) {
            case "ONE_TIME" -> q = quantity;
            case "PER_STUDENT_YEAR" -> q = studentYears.multiply(quantity);
            case "PER_MODULE_YEAR" -> q = quantity.multiply(BigDecimal.valueOf(months))
                    .divide(TWELVE, 6, RoundingMode.HALF_UP);
            case "RECURRING_FIXED", "PER_UNIT" ->
                    q = quantity.multiply(BigDecimal.valueOf(months))
                            .divide(BigDecimal.valueOf(item.getBillingIntervalMonths() == null ? 1 : item.getBillingIntervalMonths()),
                                    6, RoundingMode.HALF_UP);
            default -> { return null; }
        }
        return line(item.getItemCode(), item.getItemName(), q, unit, q.multiply(unit));
    }

    private QuoteLine priceLine(CommercialCatalogItem item, BigDecimal quantity, int months) {
        BigDecimal q;
        if ("ONE_TIME".equals(item.getPricingBasis())) {
            q = quantity;
        } else if ("PER_STUDENT_YEAR".equals(item.getPricingBasis())) {
            q = quantity.multiply(BigDecimal.valueOf(months)).divide(TWELVE, 6, RoundingMode.HALF_UP);
        } else if ("PER_MODULE_YEAR".equals(item.getPricingBasis())) {
            q = quantity.multiply(BigDecimal.valueOf(months)).divide(TWELVE, 6, RoundingMode.HALF_UP);
        } else {
            q = quantity.multiply(BigDecimal.valueOf(months))
                    .divide(BigDecimal.valueOf(item.getBillingIntervalMonths() == null ? 1 : item.getBillingIntervalMonths()),
                            6, RoundingMode.HALF_UP);
        }
        return line(item.getItemCode(), item.getItemName(), q, money(item.getUnitAmount()), q.multiply(money(item.getUnitAmount())));
    }

    private static QuoteLine line(String code, String description, BigDecimal quantity,
            BigDecimal unit, BigDecimal amount) {
        return new QuoteLine(code, description, quantity.setScale(6, RoundingMode.HALF_UP),
                unit.setScale(2, RoundingMode.HALF_UP), amount.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal discount(BigDecimal subtotal, Plan plan) {
        if (plan.getDiscountType() == null || plan.getDiscountValue() == null) return ZERO;
        BigDecimal value = "PERCENT".equals(plan.getDiscountType())
                ? subtotal.multiply(plan.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : plan.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
        return value.min(subtotal);
    }

    private static long capacityWaste(BigDecimal capacity, int requested) {
        return capacity == null ? 0 : capacity.subtract(BigDecimal.valueOf(requested))
                .max(BigDecimal.ZERO).longValue();
    }

    private static boolean exceeds(int requested, BigDecimal capacity) {
        return capacity != null && BigDecimal.valueOf(requested).compareTo(capacity) > 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record Candidate(PlanQuote quote, long fitWaste) { }
}
