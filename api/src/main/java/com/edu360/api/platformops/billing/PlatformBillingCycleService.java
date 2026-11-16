package com.edu360.api.platformops.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

 @Transactional
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
   LocalDate usageFrom=request.periodStart().withDayOfMonth(1), usageTo=request.periodEnd().withDayOfMonth(1);
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
   BigDecimal planUnit=money(plan.getPriceAmount()), planQty=BigDecimal.valueOf(plan.getBillingCycleMonths()).setScale(2);
   List<InvoiceLine> lines=new ArrayList<>();
   lines.add(line(invoiceId,"PLAN","Plan subscription: "+plan.getPlanName(),planQty,planUnit,null,now,principal.platformUserId()));
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
   String number="INV-"+LocalDate.now().getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
   Invoice invoice=Invoice.builder().invoiceId(invoiceId).tenantId(tenantId).invoiceNumber(number)
    .periodStart(request.periodStart()).periodEnd(request.periodEnd()).subtotalAmount(subtotal)
    .discountAmount(BigDecimal.ZERO.setScale(2)).totalAmount(subtotal)
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
  BigDecimal q=qty.setScale(2,RoundingMode.HALF_UP), u=unit.setScale(2,RoundingMode.HALF_UP);
  return InvoiceLine.builder().invoiceLineId(UUID.randomUUID()).invoiceId(invoiceId).lineType(type).description(description)
   .quantity(q).unitAmount(u).lineAmount(q.multiply(u).setScale(2,RoundingMode.HALF_UP))
   .tenantUsageMonthlyId(usageId).createdAt(now).createdBy(actor).updatedAt(now).updatedBy(actor).rowVersion(1).build();
 }
 private static BigDecimal money(BigDecimal value) { return value==null?BigDecimal.ZERO.setScale(2):value.setScale(2,RoundingMode.HALF_UP); }
}
