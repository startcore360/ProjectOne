package com.edu360.api.platformops.billing;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/** Derives overdue from SENT + due_at; it never sets the separate manual suspension-review flag. */
@Component
@RequiredArgsConstructor
public class PlatformInvoiceOverdueJob {
 private static final Logger log=LoggerFactory.getLogger(PlatformInvoiceOverdueJob.class);
 private static final ZoneId BILLING_ZONE=ZoneId.of("Asia/Kolkata");
 private final InvoiceRepository invoiceRepository;
 private final TenantAdminInvoiceNotifier tenantAdminNotifier;

 @Scheduled(cron="0 0 1 * * *",zone="Asia/Kolkata")
 public void scheduledDailyScan() { runDailyOverdueScan(); }

 public int runDailyOverdueScan() {
  LocalDate today=LocalDate.now(BILLING_ZONE);
  List<Invoice> overdue=invoiceRepository.findByStatusAndDueAtBeforeOrderByDueAtAsc("SENT",today);
  int notified=0;
  for(Invoice invoice:overdue) {
   try { notified+=tenantAdminNotifier.notifyOverdueInvoice(invoice); }
   catch(RuntimeException ex) { log.warn("Overdue notification failed for invoice {}",invoice.getInvoiceId(),ex); }
  }
  return notified;
 }
}
