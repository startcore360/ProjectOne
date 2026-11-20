package com.edu360.api.platformops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platformops.billing.InvoiceRepository;
import com.edu360.api.tenant.*;
class PlatformTenantRenewalServiceTest {
 private TenantRepository tenants; private PlanRepository plans; private InvoiceRepository invoices; private PlatformTenantRenewalService service;
 private Tenant tenant; private UUID tenantId,oldPlanId,newPlanId; private LocalDate renewalAt;
 @BeforeEach void setUp() {
  tenants=mock(TenantRepository.class); plans=mock(PlanRepository.class); invoices=mock(InvoiceRepository.class); service=new PlatformTenantRenewalService(tenants,plans,invoices);
  tenantId=UUID.randomUUID(); oldPlanId=UUID.randomUUID(); newPlanId=UUID.randomUUID(); renewalAt=LocalDate.of(2026,11,1);
  tenant=Tenant.builder().tenantId(tenantId).state(TenantState.ACTIVE).planId(oldPlanId).pendingPlanId(newPlanId).nextRenewalAt(renewalAt)
   .createdAt(Instant.now()).createdBy(UUID.randomUUID()).updatedAt(Instant.now()).updatedBy(UUID.randomUUID()).build();
  when(tenants.findByStateAndNextRenewalAtLessThanEqual(TenantState.ACTIVE,renewalAt)).thenReturn(List.of(tenant));
  when(invoices.existsByTenantIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(tenantId,renewalAt,renewalAt)).thenReturn(true);
  when(plans.findById(newPlanId)).thenReturn(Optional.of(Plan.builder().planId(newPlanId).billingCycleMonths(12).status("ACTIVE").build()));
  when(tenants.save(any(Tenant.class))).thenAnswer(inv->inv.getArgument(0));
 }
 @Test void ACT_RENEWAL_JOB_ALLOW_appliesPendingPlanAfterInvoiceAndAdvancesDate() {
  assertThat(service.processDueRenewals(renewalAt)).isEqualTo(1); assertThat(tenant.getPlanId()).isEqualTo(newPlanId);
  assertThat(tenant.getPendingPlanId()).isNull(); assertThat(tenant.getNextRenewalAt()).isEqualTo(renewalAt.plusMonths(12));
  assertThat(tenant.getUpdatedBy()).isEqualTo(new UUID(0L,0L)); verify(tenants).save(tenant);
 }
 @Test void ACT_RENEWAL_JOB_GUARD_doesNotApplyBeforeRenewalInvoiceExists() {
  when(invoices.existsByTenantIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(tenantId,renewalAt,renewalAt)).thenReturn(false);
  assertThat(service.processDueRenewals(renewalAt)).isZero(); verify(tenants,never()).save(any(Tenant.class));
 }
}
