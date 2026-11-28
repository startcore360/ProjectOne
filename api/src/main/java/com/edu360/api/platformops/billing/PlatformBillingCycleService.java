package com.edu360.api.platformops.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.usage.TenantUsageMonthly;
import com.edu360.api.platformops.usage.TenantUsageMonthlyRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;
import com.edu360.api.platformops.billing.dto.BillingCycleResponse;
import com.edu360.api.platformops.billing.dto.CreateBillingCycleRequest;
import com.edu360.api.platformops.billing.dto.InvoiceResponse;
import com.edu360.api.platformops.entitlement.PlanEntitlement;
import com.edu360.api.platformops.entitlement.PlanEntitlementRepository;
import com.edu360.api.platformops.subscription.CommercialCatalogItem;
import com.edu360.api.platformops.subscription.CommercialCatalogItemRepository;
import com.edu360.api.platformops.subscription.PlanComponent;
import com.edu360.api.platformops.subscription.PlanComponentRepository;
import com.edu360.api.platformops.subscription.SubscriptionQuoteCalculator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatformBillingCycleService {
 private final TenantRepository tenantRepository;
 private final PlanRepository planRepository;
 private final InvoiceRepository invoiceRepository;
 private final InvoiceLineRepository invoiceLineRepository;
 private final TenantUsageMonthlyRepository usageRepository;
 private final PlatformAuditWriter auditWriter;

 @Autowired(required = false)
 private PlanComponentRepository planComponentRepository;
 @Autowired(required = false)
 private CommercialCatalogItemRepository catalogRepository;
 @Autowired(required = false)
 private PlanEntitlementRepository planEntitlementRepository;
 @Autowired(required = false)
 private SubscriptionQuoteCalculator quoteCalculator;

 @Transactional(transactionManager = "platformTransactionManager")
 public BillingCycleResponse createBillingCycle(CreateBillingCycleRequest request, PlatformPrincipal principal) {
  if (request.periodEnd().isBefore(request.periodStart()))
   throw new BusinessRuleViolationException("INVALID_BILLING_PERIOD","Billing period end must be on or after its start.");
  List<InvoiceResponse> created=new ArrayList<>();
  List<BillingCycleResponse.SkippedTenant> skipped=new ArrayList<>();
  List<Tenant> due=tenantRepository.findByStateAndNextRenewalAtBetween(TenantState.ACTIVE,request.periodStart(),request.periodEnd());
  for(Tenant tenant:due) {
   UUID tenantId=tenant.getTenantId();
   if(invoiceRepository.existsByTenantIdAndPeriodStartAndPeriodEnd(tenantId,request.periodStart(),request.periodEnd())) {
    skipped.add(new BillingCycleResponse.SkippedTenant(tenantId,"ALREADY_INVOICED")); continue;
   }
   UUID invoicePlanId=tenant.getPendingPlanId()!=null?tenant.getPendingPlanId():tenant.getPlanId();
   Plan plan=planRepository.findById(invoicePlanId).orElse(null);
   if(plan==null) { skipped.add(new BillingCycleResponse.SkippedTenant(tenantId,"PLAN_NOT_FOUND")); continue; }
   if(tenant.getPendingPlanId()!=null&&!"ACTIVE".equals(plan.getStatus())) {
    skipped.add(new BillingCycleResponse.SkippedTenant(tenantId,"PENDING_PLAN_NOT_ACTIVE")); continue;
   }
   if(plan.getBillingCycleMonths()==null||plan.getBillingCycleMonths()<1) {
    skipped.add(new BillingCycleResponse.SkippedTenant(tenantId,"INVALID_BILLING_CYCLE")); continue;
   }
   LocalDate usageFrom=request.periodStart().withDayOfMonth(1), usageTo=request.periodEnd();
   List<TenantUsageMonthly> usage=usageRepository.findByTenantIdAndUsageMonthBetweenOrderByUsageMonthAsc(tenantId,usageFrom,usageTo);
   List<TenantUsageMonthly> billable=usage.stream().filter(r->!r.isFinal())
    .filter(r->"SMS_SENT".equals(r.getMetricCode())||"STORAGE_GB_PEAK".equals(r.getMetricCode())).toList();
   boolean smsMissing=billable.stream().anyMatch(r->"SMS_SENT".equals(r.getMetricCode())
    &&r.getQuantity().compareTo(BigDecimal.ZERO)>0&&plan.getSmsUnitAmount()==null);
   boolean storageMissing=billable.stream().anyMatch(r->"STORAGE_GB_PEAK".equals(r.getMetricCode())
    &&plan.getStorageLimitGb()!=null&&r.getQuantity().compareTo(BigDecimal.valueOf(plan.getStorageLimitGb()))>0
    &&plan.getStorageUnitAmountPerGb()==null);
   if(smsMissing||storageMissing) {
    skipped.add(new BillingCycleResponse.SkippedTenant(tenantId,smsMissing?"SMS_RATE_NOT_CONFIGURED":"STORAGE_RATE_NOT_CONFIGURED")); continue;
   }
   Instant now=Instant.now(); UUID invoiceId=UUID.randomUUID();
   List<InvoiceLine> lines=new ArrayList<>();
   String invoiceEngineVersion = tenant.getPendingPlanId() != null
    ? plan.getPricingEngineVersion() : tenant.getSubscriptionEngineVersion();
   boolean v2 = "V2".equals(invoiceEngineVersion);
   String pricingType = plan.getPricingType() == null ? "FLAT" : plan.getPricingType();
   List<PlanComponent> components = v2 && planComponentRepository != null
    ? planComponentRepository.findByPlanIdAndEnabledTrueOrderByPlanComponentId(plan.getPlanId()) : List.of();
   Map<UUID, CommercialCatalogItem> catalogItems = v2 && catalogRepository != null
    ? catalogRepository.findAllById(components.stream().map(PlanComponent::getCatalogItemId).distinct().toList())
      .stream().collect(Collectors.toMap(CommercialCatalogItem::getCatalogItemId, Function.identity()))
    : Map.of();
   boolean hasStudentYearComponent = components.stream().map(c -> catalogItems.get(c.getCatalogItemId()))
    .anyMatch(item -> item != null && "PER_STUDENT_YEAR".equals(item.getPricingBasis()));
   BigDecimal studentYears = BigDecimal.ZERO.setScale(6);
   if (v2 && ("PER_STUDENT_YEAR".equals(pricingType) || hasStudentYearComponent)) {
    List<TenantUsageMonthly> studentMonths=studentMonthsForPeriod(usage, request.periodStart(), request.periodEnd());
    int expectedMonths=expectedSnapshotMonths(request.periodStart(),request.periodEnd());
    if (expectedMonths==0||studentMonths.size()!=expectedMonths) {
     skipped.add(new BillingCycleResponse.SkippedTenant(tenantId,"BILLABLE_STUDENT_SNAPSHOT_MISSING")); continue;
    }
    studentYears=studentMonths.stream().map(TenantUsageMonthly::getQuantity)
     .reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(12),6,RoundingMode.HALF_UP);
   }
   if (v2 && "PER_STUDENT_YEAR".equals(pricingType)) {
    lines.add(line(invoiceId,"PLAN","Plan subscription: "+plan.getPlanName()+" · student-years",
     studentYears,money(plan.getPriceAmount()),null,now,principal.platformUserId()));
   } else if (v2 && "PER_MODULE".equals(pricingType)) {
    long entitledModuleCount = planEntitlementRepository == null ? 0
     : planEntitlementRepository.findByPlanId(plan.getPlanId()).stream()
      .filter(PlanEntitlement::isEnabled).filter(entitlement -> entitlement.getFeatureCode() == null)
      .map(PlanEntitlement::getModuleCode).distinct().count();
    long componentModuleCount = components.stream()
     .map(component -> catalogItems.get(component.getCatalogItemId()))
     .filter(item -> item != null && "MODULE".equals(item.getCategory()))
     .map(CommercialCatalogItem::getModuleCode).filter(java.util.Objects::nonNull).distinct().count();
    long moduleCount = Math.max(entitledModuleCount, componentModuleCount);
    if (moduleCount == 0) {
     skipped.add(new BillingCycleResponse.SkippedTenant(tenantId,"PLAN_MODULES_NOT_CONFIGURED")); continue;
    }
    BigDecimal moduleYears=BigDecimal.valueOf(moduleCount).multiply(BigDecimal.valueOf(plan.getBillingCycleMonths()))
     .divide(BigDecimal.valueOf(12),6,RoundingMode.HALF_UP);
    lines.add(line(invoiceId,"PLAN","Plan subscription: "+plan.getPlanName()+" · module-years",
     moduleYears,money(plan.getPriceAmount()),null,now,principal.platformUserId()));
   } else {
    BigDecimal planUnit=money(plan.getPriceAmount());
    BigDecimal planQty=BigDecimal.valueOf(plan.getBillingCycleMonths()).setScale(6);
    lines.add(line(invoiceId,"PLAN","Plan subscription: "+plan.getPlanName(),planQty,planUnit,null,now,principal.platformUserId()));
   }
   if (v2) {
    List<PlanComponent> billableComponents = components.stream()
     .filter(component -> {
      CommercialCatalogItem item = catalogItems.get(component.getCatalogItemId());
      if (item == null) throw new IllegalStateException("Plan component references a missing catalog item");
      if ("ONE_TIME".equals(item.getPricingBasis())
          && invoiceRepository.existsChargedCatalogItem(tenantId, item.getCatalogItemId())) return false;
      return !"PER_MODULE".equals(pricingType) || !"MODULE".equals(item.getCategory());
     }).toList();
    if (!billableComponents.isEmpty() && quoteCalculator == null) {
     throw new IllegalStateException("Subscription quote calculator is unavailable");
    }
    if (!billableComponents.isEmpty()) {
     quoteCalculator.priceComponents(billableComponents, catalogItems, studentYears, plan.getBillingCycleMonths())
      .forEach(quoteLine -> {
       CommercialCatalogItem item = billableComponents.stream()
        .map(component -> catalogItems.get(component.getCatalogItemId()))
        .filter(catalogItem -> catalogItem.getItemCode().equals(quoteLine.code())).findFirst().orElseThrow();
       String lineType = "MODULE".equals(item.getCategory()) ? "MODULE" : "ADDON";
       lines.add(line(invoiceId,lineType,quoteLine.description(),quoteLine.quantity(),quoteLine.unitAmount(),
        null,now,principal.platformUserId(),item.getCatalogItemId(),null));
      });
    }
   }
   if (v2 && plan.getSetupFeeAmount()!=null && plan.getSetupFeeAmount().signum()>0
       && !invoiceRepository.existsChargedSetupFee(tenantId)) {
    lines.add(line(invoiceId,"SETUP","One-time setup fee",BigDecimal.ONE,
     money(plan.getSetupFeeAmount()),null,now,principal.platformUserId()));
   }
   for(TenantUsageMonthly row:billable) {
    if("SMS_SENT".equals(row.getMetricCode())) {
     lines.add(line(invoiceId,"SMS","SMS usage · "+row.getUsageMonth(),money(row.getQuantity()),
      money(plan.getSmsUnitAmount()),row.getTenantUsageMonthlyId(),now,principal.platformUserId()));
    } else if("STORAGE_GB_PEAK".equals(row.getMetricCode())&&plan.getStorageLimitGb()!=null) {
     BigDecimal over=row.getQuantity().subtract(BigDecimal.valueOf(plan.getStorageLimitGb())).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);
     if(over.signum()>0) lines.add(line(invoiceId,"STORAGE","Storage overage · "+row.getUsageMonth(),over,
      money(plan.getStorageUnitAmountPerGb()),row.getTenantUsageMonthlyId(),now,principal.platformUserId()));
    }
   }
   BigDecimal subtotal=lines.stream().map(InvoiceLine::getLineAmount).reduce(BigDecimal.ZERO.setScale(2),BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
   BigDecimal discountAmount=v2?calculatePlanDiscount(subtotal,plan):BigDecimal.ZERO.setScale(2);
   String number="INV-"+LocalDate.now().getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
   Invoice invoice=Invoice.builder().invoiceId(invoiceId).tenantId(tenantId).invoiceNumber(number)
    .periodStart(request.periodStart()).periodEnd(request.periodEnd()).subtotalAmount(subtotal)
    .discountAmount(discountAmount).discountAppliedBy(discountAmount.signum()>0?principal.platformUserId():null)
    .discountType(plan.getDiscountType()).discountValue(plan.getDiscountValue())
    .totalAmount(subtotal.subtract(discountAmount))
    .currencyCode(plan.getCurrencyCode()==null?"INR":plan.getCurrencyCode()).status("DRAFT")
    .createdAt(now).createdBy(principal.platformUserId()).updatedAt(now).updatedBy(principal.platformUserId())
    .rowVersion(1).build();
   Invoice saved=invoiceRepository.saveAndFlush(invoice);
   invoiceLineRepository.saveAll(lines);
   for(TenantUsageMonthly row:billable) { row.setFinal(true); row.setUpdatedAt(now); row.setUpdatedBy(principal.platformUserId()); }
   if(!billable.isEmpty()) usageRepository.saveAll(billable);
   auditWriter.record(principal,"BILLING_CYCLE_INVOICE_CREATED","INVOICE_MANAGE","INVOICE",saved.getInvoiceId());
   created.add(InvoiceResponse.fromEntity(saved));
  }
  return new BillingCycleResponse(created,skipped);
 }

 private static InvoiceLine line(UUID invoiceId,String type,String description,BigDecimal qty,BigDecimal unit,UUID usageId,Instant now,UUID actor) {
  return line(invoiceId,type,description,qty,unit,usageId,now,actor,null,null);
 }
 private static InvoiceLine line(UUID invoiceId,String type,String description,BigDecimal qty,BigDecimal unit,
   UUID usageId,Instant now,UUID actor,UUID catalogItemId,UUID subscriptionAddonId) {
  BigDecimal q=qty.setScale(6,RoundingMode.HALF_UP), u=unit.setScale(2,RoundingMode.HALF_UP);
  return InvoiceLine.builder().invoiceLineId(UUID.randomUUID()).invoiceId(invoiceId).lineType(type).description(description)
   .quantity(q).unitAmount(u).lineAmount(q.multiply(u).setScale(2,RoundingMode.HALF_UP))
   .tenantUsageMonthlyId(usageId).catalogItemId(catalogItemId).subscriptionAddonId(subscriptionAddonId)
   .createdAt(now).createdBy(actor).updatedAt(now).updatedBy(actor).rowVersion(1).build();
 }
 private static BigDecimal money(BigDecimal value) { return value==null?BigDecimal.ZERO.setScale(2):value.setScale(2,RoundingMode.HALF_UP); }

 private static BigDecimal calculatePlanDiscount(BigDecimal subtotal, Plan plan) {
  if(plan.getDiscountType()==null||plan.getDiscountValue()==null||subtotal.signum()<=0)return BigDecimal.ZERO.setScale(2);
  BigDecimal amount="PERCENT".equals(plan.getDiscountType())
   ? subtotal.multiply(plan.getDiscountValue()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP)
   : plan.getDiscountValue().setScale(2,RoundingMode.HALF_UP);
  return amount.min(subtotal);
 }

 private static int expectedSnapshotMonths(LocalDate periodStart, LocalDate periodEnd) {
  int count=0;
  for(LocalDate month=periodStart.withDayOfMonth(1);!month.isAfter(periodEnd.withDayOfMonth(1));month=month.plusMonths(1)) {
   LocalDate monthEnd=month.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
   if(!monthEnd.isBefore(periodStart)&&!monthEnd.isAfter(periodEnd))count++;
  }
  return count;
 }
 private static List<TenantUsageMonthly> studentMonthsForPeriod(List<TenantUsageMonthly> usage,
   LocalDate periodStart, LocalDate periodEnd) {
  return usage.stream().filter(r->"BILLABLE_ACTIVE_STUDENTS".equals(r.getMetricCode())&&r.isFinal()&&r.isMeasured())
   .filter(r->{
    LocalDate monthEnd=r.getUsageMonth().plusMonths(1).minusDays(1);
    return !monthEnd.isBefore(periodStart)&&!monthEnd.isAfter(periodEnd);
   }).toList();
 }
}
