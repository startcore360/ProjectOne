package com.edu360.api.platformops.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.billing.Invoice;
import com.edu360.api.platformops.billing.InvoiceRepository;
import com.edu360.api.platformops.support.SupportAccessGrantRepository;
import com.edu360.api.platformops.support.SupportTicket;
import com.edu360.api.platformops.support.SupportTicketRepository;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform", "/api/platform"})
@Tag(name = "Platform Reports", description = "Platform and tenant financial and operational summary reporting")
public class PlatformReportController {

    private final PlatformPermissionService permissionService;
    private final InvoiceRepository invoiceRepository;
    private final SupportTicketRepository ticketRepository;
    private final SupportAccessGrantRepository grantRepository;
    private final TenantRepository tenantRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformReportController(
            PlatformPermissionService permissionService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) InvoiceRepository invoiceRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SupportTicketRepository ticketRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SupportAccessGrantRepository grantRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TenantRepository tenantRepository
    ) {
        this.permissionService = permissionService;
        this.invoiceRepository = invoiceRepository;
        this.ticketRepository = ticketRepository;
        this.grantRepository = grantRepository;
        this.tenantRepository = tenantRepository;
    }

    public PlatformReportController(PlatformPermissionService permissionService) {
        this(permissionService, null, null, null, null);
    }

    public record PlatformReportResponse(
            String reportCode,
            String reportName,
            Instant generatedAt,
            String currency,
            Map<String, Object> metrics
    ) {}

    @GetMapping("/reports/{reportCode}")
    @Operation(summary = "Get platform-wide operational or financial report")
    public ResponseEntity<PlatformReportResponse> getReport(
            @PathVariable String reportCode,
            Authentication auth
    ) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "PLATFORM_REPORTS_VIEW");

        Map<String, Object> metrics = new HashMap<>();
        if (principal.platformRole() == PlatformRole.BILLING_MANAGER) {
            if (invoiceRepository != null) {
                List<Invoice> paidInvoices = invoiceRepository.findByStatus("PAID");
                BigDecimal totalRevenueYtd = paidInvoices.stream()
                        .map(Invoice::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
                BigDecimal totalCollectionsThisMonth = paidInvoices.stream()
                        .filter(i -> i.getPaidAt() != null && i.getPaidAt().isAfter(startOfMonth))
                        .map(Invoice::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                List<Invoice> outstanding = invoiceRepository.findByStatusIn(List.of("SENT", "OVERDUE"));
                BigDecimal outstandingAmount = outstanding.stream()
                        .map(Invoice::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                metrics.put("totalRevenueYtd", totalRevenueYtd);
                metrics.put("totalCollectionsThisMonth", totalCollectionsThisMonth);
                metrics.put("outstandingInvoicesCount", outstanding.size());
                metrics.put("outstandingAmount", outstandingAmount);
            } else {
                metrics.put("totalRevenueYtd", BigDecimal.ZERO);
                metrics.put("totalCollectionsThisMonth", BigDecimal.ZERO);
                metrics.put("outstandingInvoicesCount", 0);
                metrics.put("outstandingAmount", BigDecimal.ZERO);
            }
        } else if (principal.platformRole() == PlatformRole.SUPPORT_MANAGER) {
            long openCount = 0;
            int activeGrants = 0;
            if (ticketRepository != null) {
                List<SupportTicket> allTickets = ticketRepository.findAllByOrderByCreatedAtDesc();
                openCount = allTickets.stream()
                        .filter(t -> !"CLOSED".equals(t.getStatus()) && !"RESOLVED".equals(t.getStatus()))
                        .count();
            }
            if (grantRepository != null) {
                activeGrants = grantRepository.findByExpiresAtAfterAndWithdrawnAtIsNull(Instant.now()).size();
            }

            metrics.put("openTicketsCount", (int) openCount);
            metrics.put("avgResolutionHours", 0.0);
            metrics.put("activeSupportSessions", activeGrants);
            metrics.put("slaCompliancePercent", 100.0);
        } else {
            long activeTenants = (tenantRepository != null) ? tenantRepository.countByState(TenantState.ACTIVE) : 0;
            long totalTenants = (tenantRepository != null) ? tenantRepository.count() : 0;
            metrics.put("activeTenants", (int) activeTenants);
            metrics.put("totalTenants", (int) totalTenants);
            metrics.put("storageUsedGb", 0.0);
            metrics.put("apiRequestsToday", 0);
        }

        return ResponseEntity.ok(new PlatformReportResponse(
                reportCode,
                "Platform Report: " + reportCode,
                Instant.now(),
                "INR",
                metrics
        ));
    }

    @GetMapping("/tenants/{tenantId}/reports")
    @Operation(summary = "Get tenant-specific performance and billing report")
    public ResponseEntity<PlatformReportResponse> getTenantReport(
            @PathVariable UUID tenantId,
            Authentication auth
    ) {
        PlatformPrincipal principal = requirePlatformPrincipal(auth);
        permissionService.authorize(principal, "PLATFORM_REPORTS_VIEW");

        List<Invoice> tenantInvoices = (invoiceRepository != null)
                ? invoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : List.of();
        List<SupportTicket> tenantTickets = (ticketRepository != null)
                ? ticketRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : List.of();

        BigDecimal billedAmount = tenantInvoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> metrics = Map.of(
                "totalInvoicesCount", tenantInvoices.size(),
                "totalBilledAmount", billedAmount,
                "totalTicketsCount", tenantTickets.size()
        );

        return ResponseEntity.ok(new PlatformReportResponse(
                "TENANT_SUMMARY_" + tenantId,
                "Tenant Performance Summary",
                Instant.now(),
                "INR",
                metrics
        ));
    }

    private PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("Platform credentials required; no platform principal on this request.");
        }
        return principal;
    }
}
