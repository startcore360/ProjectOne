package com.edu360.api.platformops.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.usage.TenantUsageMonthly;
import com.edu360.api.platformops.usage.TenantUsageMonthlyRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;
import com.edu360.api.platformops.billing.dto.CreateBillingCycleRequest;

class PlatformBillingCycleServiceTest {
 private TenantRepository tenantRepository; private PlanRepository planRepository; private InvoiceRepository invoiceRepository;
 private InvoiceLineRepository invoiceLineRepository; private TenantUsageMonthlyRepository usageRepository;
 private PlatformAuditWriter auditWriter; private PlatformBillingCycleService service; private PlatformPrincipal actor;
 private Tenant tenant; private Plan plan; private UUID tenantId,planId;
 private final LocalDate start=LocalDate.of(2026,11,1), end=LocalDate.of(2026,11,30);

 @BeforeEach void setUp() {
  tenantRepository=mock(TenantRepository.class); planRepository=mock(PlanRepository.class); invoiceRepository=mock(InvoiceRepository.class);
  invoiceLineRepository=mock(InvoiceLineRepository.class); usageRepository=mock(TenantUsageMonthlyRepository.class); auditWriter=mock(PlatformAuditWriter.class);
  service=new PlatformBillingCycleService(tenantRepository,planRepository,invoiceRepository,invoiceLineRepository,usageRepository,auditWriter);
  actor=new PlatformPrincipal(UUID.randomUUID(),PlatformRole.BILLING_MANAGER,"billing@edu360.local",false);
  tenantId=UUID.randomUUID(); planId=UUID.randomUUID();
  tenant=Tenant.builder().tenantId(tenantId).tenantName("Billing tenant").tenantType("STANDALONE_SCHOOL").planId(planId)
   .state(TenantState.ACTIVE).nextRenewalAt(LocalDate.of(2026,11,15)).subdomain("billingtenant")
   .createdBy(actor.platformUserId()).updatedBy(actor.platformUserId()).build();
  plan=Plan.builder().planId(planId).planName("Standard").priceAmount(new BigDecimal("1000.00")).currencyCode("INR")
   .billingCycleMonths(1).storageLimitGb(10).smsUnitAmount(new BigDecimal("0.10"))
   .storageUnitAmountPerGb(new BigDecimal("5.00")).build();
  when(tenantRepository.findByStateAndNextRenewalAtBetween(TenantState.ACTIVE,start,end)).thenReturn(List.of(tenant));
  when(invoiceRepository.existsByTenantIdAndPeriodStartAndPeriodEnd(tenantId,start,end)).thenReturn(false);
  when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
  when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv->inv.getArgument(0));
  when(invoiceLineRepository.saveAll(anyList())).thenAnswer(inv->inv.getArgument(0));
  when(usageRepository.saveAll(anyList())).thenAnswer(inv->inv.getArgument(0));
 }

 @Test void ACT_BILLING_CYCLE_ALLOW_createsPlanSmsAndStorageFromActualUsageRows() {
  TenantUsageMonthly sms=usage("SMS_SENT","120.00"), storage=usage("STORAGE_GB_PEAK","12.50");
  when(usageRepository.findByTenantIdAndUsageMonthBetweenOrderByUsageMonthAsc(tenantId,start,end)).thenReturn(List.of(sms,storage));
  var result=service.createBillingCycle(new CreateBillingCycleRequest(start,end),actor);
  assertThat(result.created()).hasSize(1); assertThat(result.created().get(0).subtotalAmount()).isEqualByComparingTo("1024.50");
  assertThat(result.created().get(0).status()).isEqualTo("DRAFT"); assertThat(sms.isFinal()).isTrue(); assertThat(storage.isFinal()).isTrue();
  org.mockito.ArgumentCaptor<List<InvoiceLine>> captor=org.mockito.ArgumentCaptor.forClass(List.class);
  verify(invoiceLineRepository).saveAll(captor.capture());
  assertThat(captor.getValue()).extracting(InvoiceLine::getLineType).containsExactly("PLAN","SMS","STORAGE");
  assertThat(captor.getValue()).extracting(InvoiceLine::getLineAmount).containsExactly(new BigDecimal("1000.00"),new BigDecimal("12.00"),new BigDecimal("12.50"));
  verify(tenantRepository).findByStateAndNextRenewalAtBetween(TenantState.ACTIVE,start,end);
 }

 @Test void ACT_BILLING_CYCLE_GUARD_noUsageRowsDoesNotEstimateMeteredUsage() {
  when(usageRepository.findByTenantIdAndUsageMonthBetweenOrderByUsageMonthAsc(tenantId,start,end)).thenReturn(List.of());
  var result=service.createBillingCycle(new CreateBillingCycleRequest(start,end),actor);
  assertThat(result.created()).hasSize(1); assertThat(result.created().get(0).subtotalAmount()).isEqualByComparingTo("1000.00");
  org.mockito.ArgumentCaptor<List<InvoiceLine>> captor=org.mockito.ArgumentCaptor.forClass(List.class);
  verify(invoiceLineRepository).saveAll(captor.capture());
  assertThat(captor.getValue()).extracting(InvoiceLine::getLineType).containsExactly("PLAN");
 }

 @Test void ACT_BILLING_CYCLE_GUARD_duplicatePeriodIsSkipped() {
  when(invoiceRepository.existsByTenantIdAndPeriodStartAndPeriodEnd(tenantId,start,end)).thenReturn(true);
  var result=service.createBillingCycle(new CreateBillingCycleRequest(start,end),actor);
  assertThat(result.created()).isEmpty(); assertThat(result.skipped()).singleElement().satisfies(x->assertThat(x.reason()).isEqualTo("ALREADY_INVOICED"));
  org.mockito.Mockito.verify(invoiceRepository,org.mockito.Mockito.never()).saveAndFlush(any(Invoice.class));
 }

 @Test void ACT_BILLING_CYCLE_GUARD_missingSmsRateSkipsTenantInsteadOfGuessing() {
  plan.setSmsUnitAmount(null);
  when(usageRepository.findByTenantIdAndUsageMonthBetweenOrderByUsageMonthAsc(tenantId,start,end)).thenReturn(List.of(usage("SMS_SENT","10")));
  var result=service.createBillingCycle(new CreateBillingCycleRequest(start,end),actor);
  assertThat(result.created()).isEmpty(); assertThat(result.skipped()).singleElement().satisfies(x->assertThat(x.reason()).isEqualTo("SMS_RATE_NOT_CONFIGURED"));
 }

 @Test void ACT_BILLING_CYCLE_V2_billsStudentYearsFromFinalEnrollmentSnapshot() {
  tenant.setSubscriptionEngineVersion("V2");
  plan.setPricingType("PER_STUDENT_YEAR");
  plan.setPricingEngineVersion("V2");
  plan.setPriceAmount(new BigDecimal("120.00"));
  TenantUsageMonthly snapshot=usage("BILLABLE_ACTIVE_STUDENTS","100");
  snapshot.setFinal(true);
  snapshot.setMeasured(true);
  when(usageRepository.findByTenantIdAndUsageMonthBetweenOrderByUsageMonthAsc(tenantId,start,end))
   .thenReturn(List.of(snapshot));

  var result=service.createBillingCycle(new CreateBillingCycleRequest(start,end),actor);

  assertThat(result.created()).hasSize(1);
  assertThat(result.created().get(0).subtotalAmount()).isEqualByComparingTo("1000.00");
  org.mockito.ArgumentCaptor<List<InvoiceLine>> captor=org.mockito.ArgumentCaptor.forClass(List.class);
  verify(invoiceLineRepository).saveAll(captor.capture());
  assertThat(captor.getValue()).singleElement().satisfies(line -> {
   assertThat(line.getQuantity()).isEqualByComparingTo("8.333333");
   assertThat(line.getUnitAmount()).isEqualByComparingTo("120.00");
   assertThat(line.getLineAmount()).isEqualByComparingTo("1000.00");
  });
 }

 private TenantUsageMonthly usage(String metric,String quantity) {
  Instant now=Instant.now();
  return TenantUsageMonthly.builder().tenantUsageMonthlyId(UUID.randomUUID()).tenantId(tenantId).usageMonth(start)
   .metricCode(metric).quantity(new BigDecimal(quantity)).computedAt(now).isFinal(false).createdAt(now)
   .createdBy(actor.platformUserId()).updatedAt(now).updatedBy(actor.platformUserId()).rowVersion(1).build();
 }
}
