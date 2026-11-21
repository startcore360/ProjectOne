package com.edu360.api.platformops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.billing.*;
import com.edu360.api.platformops.dto.*;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.*;
class PlatformTenantPlanChangeServiceTest {
 private TenantRepository tenants; private PlanRepository plans; private InvoiceRepository invoices; private InvoiceLineRepository lines;
 private PlatformAuditWriter audit; private PlatformTenantPlanChangeService service; private PlatformPrincipal owner;
 private Tenant tenant; private Plan current,target; private UUID tenantId,currentId,targetId; private LocalDate today;
 @BeforeEach void setUp() {
  tenants=mock(TenantRepository.class); plans=mock(PlanRepository.class); invoices=mock(InvoiceRepository.class); lines=mock(InvoiceLineRepository.class); audit=mock(PlatformAuditWriter.class);
  service=new PlatformTenantPlanChangeService(tenants,plans,invoices,lines,audit);
  owner=new PlatformPrincipal(UUID.randomUUID(),PlatformRole.PRODUCT_OWNER,"owner@edu360.local",false);
  tenantId=UUID.randomUUID(); currentId=UUID.randomUUID(); targetId=UUID.randomUUID(); today=LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
  tenant=Tenant.builder().tenantId(tenantId).state(TenantState.ACTIVE).planId(currentId).nextRenewalAt(today.plusDays(100))
   .createdAt(Instant.now()).createdBy(owner.platformUserId()).updatedAt(Instant.now()).updatedBy(owner.platformUserId()).build();
  current=Plan.builder().planId(currentId).planName("Basic").priceAmount(new BigDecimal("10000.00")).currencyCode("INR").status("ACTIVE").build();
  target=Plan.builder().planId(targetId).planName("Premium").priceAmount(new BigDecimal("20000.00")).currencyCode("INR").status("ACTIVE").build();
  when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant)); when(plans.findById(currentId)).thenReturn(Optional.of(current)); when(plans.findById(targetId)).thenReturn(Optional.of(target));
  when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(inv->inv.getArgument(0)); when(lines.saveAndFlush(any(InvoiceLine.class))).thenAnswer(inv->inv.getArgument(0));
  when(tenants.save(any(Tenant.class))).thenAnswer(inv->inv.getArgument(0));
 }
 @Test void ACT_PLAN_CHANGE_ALLOW_nowUpgradeCreatesProratedDraftInvoiceAndClearsPendingPlan() {
  tenant.setPendingPlanId(UUID.randomUUID());
  var result=service.changePlan(tenantId,new ChangeTenantPlanRequest(targetId,PlanChangeEffective.NOW),owner);
  BigDecimal expected=new BigDecimal("10000.00").multiply(new BigDecimal("100")).divide(new BigDecimal("365"),2,java.math.RoundingMode.HALF_UP);
  assertThat(result.activePlanId()).isEqualTo(targetId); assertThat(result.pendingPlanId()).isNull();
  assertThat(result.prorationInvoice().subtotalAmount()).isEqualByComparingTo(expected); assertThat(result.prorationInvoice().status()).isEqualTo("DRAFT");
  assertThat(tenant.getPlanId()).isEqualTo(targetId); assertThat(tenant.getPendingPlanId()).isNull();
  verify(lines).saveAndFlush(argThat(line->"PLAN".equals(line.getLineType())&&line.getLineAmount().compareTo(expected)==0));
 }
 @Test void ACT_PLAN_CHANGE_ALLOW_renewalDowngradeStoresPendingPlan() {
  target.setPriceAmount(new BigDecimal("5000.00"));
  when(invoices.existsByTenantIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(tenantId,tenant.getNextRenewalAt(),tenant.getNextRenewalAt())).thenReturn(false);
  var result=service.changePlan(tenantId,new ChangeTenantPlanRequest(targetId,PlanChangeEffective.RENEWAL),owner);
  assertThat(result.activePlanId()).isEqualTo(currentId); assertThat(result.pendingPlanId()).isEqualTo(targetId);
  assertThat(result.prorationInvoice()).isNull(); assertThat(tenant.getPlanId()).isEqualTo(currentId); assertThat(tenant.getPendingPlanId()).isEqualTo(targetId);
  verifyNoInteractions(lines);
 }
 @Test void ACT_PLAN_CHANGE_DENY_billingManagerCannotChangePlan() {
  PlatformPrincipal billing=new PlatformPrincipal(UUID.randomUUID(),PlatformRole.BILLING_MANAGER,"billing@edu360.local",false);
  assertThatThrownBy(()->service.changePlan(tenantId,new ChangeTenantPlanRequest(targetId,PlanChangeEffective.NOW),billing)).isInstanceOf(UnauthorizedException.class);
  verifyNoInteractions(tenants,plans,invoices,lines);
 }
 @Test void ACT_PLAN_CHANGE_SCOPE_missingTenantCannotBeChanged() {
  when(tenants.findById(tenantId)).thenReturn(Optional.empty());
  assertThatThrownBy(()->service.changePlan(tenantId,new ChangeTenantPlanRequest(targetId,PlanChangeEffective.NOW),owner)).isInstanceOf(ResourceNotFoundException.class);
 }
 @Test void ACT_PLAN_CHANGE_GUARD_immediateDowngradeRejected() {
  target.setPriceAmount(new BigDecimal("5000.00"));
  assertThatThrownBy(()->service.changePlan(tenantId,new ChangeTenantPlanRequest(targetId,PlanChangeEffective.NOW),owner))
   .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode","IMMEDIATE_DOWNGRADE_NOT_ALLOWED");
 }
 @Test void ACT_PLAN_CHANGE_GUARD_v2PlanRequiresRenewalEffectiveDate() {
  target.setPricingEngineVersion("V2");
  assertThatThrownBy(()->service.changePlan(tenantId,new ChangeTenantPlanRequest(targetId,PlanChangeEffective.NOW),owner))
   .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode","V2_PLAN_CHANGE_REQUIRES_RENEWAL");
  verify(tenants,never()).save(any(Tenant.class));
  verifyNoInteractions(lines);
 }
 @Test void ACT_PLAN_CHANGE_GUARD_cannotScheduleAfterRenewalInvoiceExists() {
  when(invoices.existsByTenantIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(tenantId,tenant.getNextRenewalAt(),tenant.getNextRenewalAt())).thenReturn(true);
  assertThatThrownBy(()->service.changePlan(tenantId,new ChangeTenantPlanRequest(targetId,PlanChangeEffective.RENEWAL),owner))
   .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode","RENEWAL_INVOICE_ALREADY_CREATED");
 }
}
