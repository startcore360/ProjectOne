package com.edu360.api.platformops;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Daily E3-S7 renewal reminder dispatch, using the platform billing calendar. */
@Component
@RequiredArgsConstructor
public class PlatformRenewalReminderJob {
    private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Kolkata");
    private final PlatformRenewalReminderService reminderService;

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    public void sendDailyRenewalReminders() {
        reminderService.sendDueRenewalReminders(LocalDate.now(BILLING_ZONE));
    }
}
