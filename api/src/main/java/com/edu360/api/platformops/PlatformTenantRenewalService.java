package com.edu360.api.platformops;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platformops.billing.InvoiceRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;
import lombok.RequiredArgsConstructor;

/** Applies scheduled plan changes only after a renewal-period invoice exists. */
@Service
@RequiredArgsConstructor
public class PlatformTenantRenewalService {
 private static final Logger log=LoggerFactory.getLogger(PlatformTenantRenewalService.class);
 private static final UUID SYSTEM_ACTOR=new UUID(0L,0L);
 private final TenantRepository tenantRepository;
 private final PlanRepository planRepository;
 private final InvoiceRepository invoiceRepository;

 @Transactional
 public int processDueRenewals(LocalDate today) {
  List<Tenant> due=tenantRepository.findByStateAndNextRenewalAtLessThanEqual(TenantState.ACTIVE,today);
  int processed=0;
  for(Tenant tenant:due) {
   LocalDate renewalAt=tenant.getNextRenewalAt();
   if(renewalAt==null||!invoiceRepository.existsByTenantIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
     tenant.getTenantId(),renewalAt,renewalAt)) continue;
   UUID effectivePlanId=tenant.getPendingPlanId()!=null?tenant.getPendingPlanId():tenant.getPlanId();
   Plan plan=planRepository.findById(effectivePlanId).orElse(null);
   if(plan==null||plan.getBillingCycleMonths()==null||plan.getBillingCycleMonths()<1) {
    log.warn("Cannot advance renewal for tenant {}; plan {} is missing or invalid",tenant.getTenantId(),effectivePlanId);
    continue;
   }
   if(tenant.getPendingPlanId()!=null&&!"ACTIVE".equals(plan.getStatus())) {
    log.warn("Cannot apply pending plan {} for tenant {}; plan is not ACTIVE",effectivePlanId,tenant.getTenantId());
    continue;
   }
   tenant.setPlanId(effectivePlanId);
   tenant.setPendingPlanId(null);
   tenant.setNextRenewalAt(renewalAt.plusMonths(plan.getBillingCycleMonths()));
   tenant.setUpdatedAt(Instant.now());
   tenant.setUpdatedBy(SYSTEM_ACTOR);
   tenantRepository.save(tenant);
   processed++;
  }
  return processed;
 }
}
