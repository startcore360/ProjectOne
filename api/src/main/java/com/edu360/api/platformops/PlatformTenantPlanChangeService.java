package com.edu360.api.platformops;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.billing.Invoice;
import com.edu360.api.platformops.billing.InvoiceLine;
import com.edu360.api.platformops.billing.InvoiceRepository;
import com.edu360.api.platformops.billing.InvoiceLineRepository;
import com.edu360.api.platformops.billing.dto.InvoiceResponse;
import com.edu360.api.platformops.dto.ChangeTenantPlanRequest;
import com.edu360.api.platformops.dto.PlanChangeEffective;
import com.edu360.api.platformops.dto.TenantPlanChangeResponse;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatformTenantPlanChangeService {
 private static final ZoneId BILLING_ZONE=ZoneId.of("Asia/Kolkata");
 private final TenantRepository tenantRepository;
 private final PlanRepository planRepository;
 private final InvoiceRepository invoiceRepository;
 private final InvoiceLineRepository invoiceLineRepository;
 private final PlatformAuditWriter auditWriter;

 @Transactional
 public TenantPlanChangeResponse changePlan(UUID tenantId,ChangeTenantPlanRequest request,PlatformPrincipal actor) {
  if(actor.platformRole()!=PlatformRole.PRODUCT_OWNER&&actor.platformRole()!=PlatformRole.PLATFORM_ADMIN)
   throw new UnauthorizedException("Only Product Owner or Platform Admin may change a tenant plan.");
  Tenant tenant=tenantRepository.findById(tenantId).orElseThrow(()->new ResourceNotFoundException("Tenant not found: "+tenantId));
  if(tenant.getState()!=TenantState.ACTIVE) throw new ApiException(HttpStatus.CONFLICT,"TENANT_NOT_ACTIVE","Plan changes require an ACTIVE tenant.");
  Plan current=planRepository.findById(tenant.getPlanId()).orElseThrow(()->new ResourceNotFoundException("Current tenant plan not found."));
  Plan target=planRepository.findById(request.planId()).orElseThrow(()->new ResourceNotFoundException("Target plan not found: "+request.planId()));
  if(!"ACTIVE".equals(target.getStatus())) throw new ApiException(HttpStatus.CONFLICT,"PLAN_NOT_ACTIVE","The selected plan is not active.");
  if(current.getPlanId().equals(target.getPlanId())) throw new ApiException(HttpStatus.CONFLICT,"PLAN_UNCHANGED","The tenant already uses this plan.");
  LocalDate today=LocalDate.now(BILLING_ZONE), renewal=tenant.getNextRenewalAt();
  if(renewal==null||!renewal.isAfter(today)) throw new ApiException(HttpStatus.CONFLICT,"RENEWAL_DATE_REQUIRED","A future renewal date is required for a plan change.");

  InvoiceResponse proration=null;
  UUID oldPendingPlanId=tenant.getPendingPlanId();
  if(request.effective()==PlanChangeEffective.NOW) {
   if(target.getPriceAmount().compareTo(current.getPriceAmount())<=0)
    throw new ApiException(HttpStatus.CONFLICT,"IMMEDIATE_DOWNGRADE_NOT_ALLOWED","Downgrades must take effect at renewal.");
   long remainingDays=ChronoUnit.DAYS.between(today,renewal);
   BigDecimal delta=target.getPriceAmount().subtract(current.getPriceAmount());
   BigDecimal amount=delta.multiply(BigDecimal.valueOf(remainingDays)).divide(BigDecimal.valueOf(365),2,RoundingMode.HALF_UP);
   Instant now=Instant.now(); UUID invoiceId=UUID.randomUUID();
   String invoiceNumber="INV-"+today.getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
   Invoice invoice=Invoice.builder().invoiceId(invoiceId).tenantId(tenantId).invoiceNumber(invoiceNumber)
    .periodStart(today).periodEnd(renewal).subtotalAmount(amount).discountAmount(BigDecimal.ZERO.setScale(2))
    .totalAmount(amount).currencyCode(target.getCurrencyCode()).status("DRAFT")
    .createdAt(now).createdBy(actor.platformUserId()).updatedAt(now).updatedBy(actor.platformUserId()).rowVersion(1).build();
   Invoice saved=invoiceRepository.saveAndFlush(invoice);
   InvoiceLine line=InvoiceLine.builder().invoiceLineId(UUID.randomUUID()).invoiceId(invoiceId).lineType("PLAN")
    .description("Pro-rata upgrade from "+current.getPlanName()+" to "+target.getPlanName()+" for "+remainingDays+" remaining days")
    .quantity(BigDecimal.ONE.setScale(2)).unitAmount(amount).lineAmount(amount).createdAt(now).createdBy(actor.platformUserId())
    .updatedAt(now).updatedBy(actor.platformUserId()).rowVersion(1).build();
   invoiceLineRepository.saveAndFlush(line);
   tenant.setPlanId(target.getPlanId());
   tenant.setPendingPlanId(null);
   proration=InvoiceResponse.fromEntity(saved);
  } else {
   if(invoiceRepository.existsByTenantIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(tenantId,renewal,renewal))
    throw new ApiException(HttpStatus.CONFLICT,"RENEWAL_INVOICE_ALREADY_CREATED","A renewal invoice already exists; the plan cannot be changed for this renewal.");
   tenant.setPendingPlanId(target.getPlanId());
  }
  tenant.setUpdatedAt(Instant.now()); tenant.setUpdatedBy(actor.platformUserId());
  tenantRepository.save(tenant);
  auditWriter.record(actor,null,"TENANT_PLAN_CHANGED","UPDATE","TENANT_SETTINGS","TENANT",tenantId,PlatformAuditWriter.SUCCESS,
   Map.of("plan_id",Map.of("old",current.getPlanId().toString(),"new",request.effective()==PlanChangeEffective.NOW?target.getPlanId().toString():current.getPlanId().toString()),
    "pending_plan_id",Map.of("old",oldPendingPlanId==null?"":oldPendingPlanId.toString(),"new",tenant.getPendingPlanId()==null?"":tenant.getPendingPlanId().toString()),
    "effective",Map.of("new",request.effective().name())));
  return new TenantPlanChangeResponse(tenantId,current.getPlanId(),tenant.getPlanId(),tenant.getPendingPlanId(),request.effective(),tenant.getNextRenewalAt(),proration);
 }
}
