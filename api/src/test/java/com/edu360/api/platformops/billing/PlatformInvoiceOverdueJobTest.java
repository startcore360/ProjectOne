package com.edu360.api.platformops.billing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class PlatformInvoiceOverdueJobTest {
 @Test void ACT_OVERDUE_JOB_ALLOW_scansSentInvoicesPastDueAndNotifiesWithoutManualFlag() {
  InvoiceRepository repository=mock(InvoiceRepository.class); TenantAdminInvoiceNotifier notifier=mock(TenantAdminInvoiceNotifier.class);
  Invoice invoice=Invoice.builder().invoiceId(UUID.randomUUID()).status("SENT").dueAt(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(1)).overdueFlaggedAt(null).build();
  when(repository.findByStatusAndDueAtBeforeOrderByDueAtAsc(eq("SENT"),any(LocalDate.class))).thenReturn(List.of(invoice));
  when(notifier.notifyOverdueInvoice(invoice)).thenReturn(1);
  PlatformInvoiceOverdueJob job=new PlatformInvoiceOverdueJob(repository,notifier);
  assertThat(job.runDailyOverdueScan()).isEqualTo(1);
  assertThat(invoice.getOverdueFlaggedAt()).isNull();
  verify(notifier).notifyOverdueInvoice(invoice);
 }
}
