package com.edu360.api.platformops.landing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.files.FileScanner;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformAccessLookup;
import com.edu360.api.platformops.PlatformConcurrency;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.landing.dto.PublicEnquiryRequest;
import com.edu360.api.platformops.landing.dto.PublicEnquiryResponse;
import com.edu360.api.platformops.landing.dto.PublicInstitutionItem;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.subscription.CommercialCatalogItemRepository;
import com.edu360.api.platformops.subscription.PlatformSubscriptionService;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

class LandingCmsServiceTest {

    @Test
    void listPublicInstitutionsReturnsOnlyOptedInTenants() {
        TenantRepository tenantRepo = mock(TenantRepository.class);
        LandingCmsService service = createService(tenantRepo, mock(PlatformSubscriptionService.class));

        Tenant t1 = Tenant.builder()
                .tenantId(UUID.randomUUID())
                .tenantName("Greenwood University")
                .subdomain("greenwood")
                .tenantType("UNIVERSITY")
                .state(TenantState.ACTIVE)
                .listedInDirectory(true)
                .build();
        when(tenantRepo.findByStateInAndListedInDirectoryTrueOrderByTenantNameAsc(
                List.of(TenantState.ACTIVE, TenantState.TRIAL)))
                .thenReturn(List.of(t1));

        List<PublicInstitutionItem> items = service.listPublicInstitutions("green");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).slug()).isEqualTo("greenwood");
        assertThat(items.get(0).name()).isEqualTo("Greenwood University");
    }

    @Test
    void submitPublicEnquiryComputesInternalQuoteSnapshotWithoutExposingPricing() {
        TenantRepository tenantRepo = mock(TenantRepository.class);
        PlatformSubscriptionService subService = mock(PlatformSubscriptionService.class);
        LandingEnquiryRepository enquiryRepo = mock(LandingEnquiryRepository.class);
        when(enquiryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subService.quote(any())).thenReturn(new CustomerQuoteResponse(
                "INR", 12, List.of(), null, null));

        LandingCmsService service = new LandingCmsService(
                tenantRepo,
                mock(LandingPageConfigRepository.class),
                mock(LandingTestimonialRepository.class),
                enquiryRepo,
                mock(CommercialCatalogItemRepository.class),
                subService,
                new PlatformConcurrency(),
                mock(PlatformAuditWriter.class)
        );

        PublicEnquiryResponse resp = service.submitPublicEnquiry(new PublicEnquiryRequest(
                "QUOTATION",
                "University",
                "1,501–3,000",
                2,
                0,
                2200,
                150,
                12,
                true,
                List.of("ACADEMIC", "FEES"),
                "Spreadsheets",
                "Remote",
                "Dr. Asha",
                "asha@greenwood.edu",
                "+91 9876543210",
                "Greenwood University",
                "Need rollout in July"
        ));

        assertThat(resp.enquiryType()).isEqualTo("QUOTATION");
        assertThat(resp.status()).isEqualTo("NEW");
        verify(subService).quote(any());
        verify(enquiryRepo).save(any());
    }

    @Test
    void submitQuotationPersistsResolvedStudentEstimateFromSelectedRange() {
        TenantRepository tenantRepo = mock(TenantRepository.class);
        PlatformSubscriptionService subService = mock(PlatformSubscriptionService.class);
        LandingEnquiryRepository enquiryRepo = mock(LandingEnquiryRepository.class);
        when(enquiryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subService.quote(any())).thenReturn(new CustomerQuoteResponse(
                "INR", 12, List.of(), null, null));

        LandingCmsService service = new LandingCmsService(
                tenantRepo,
                mock(LandingPageConfigRepository.class),
                mock(LandingTestimonialRepository.class),
                enquiryRepo,
                mock(CommercialCatalogItemRepository.class),
                subService,
                new PlatformConcurrency(),
                mock(PlatformAuditWriter.class)
        );

        service.submitPublicEnquiry(new PublicEnquiryRequest(
                "QUOTATION", "University", "1,501–3,000",
                0, 0, 0, 0, 12, true, List.of("ACADEMICS"),
                "No / not sure", "Discuss with me",
                "Asha", "asha@example.edu", null, "Example University", null
        ));

        ArgumentCaptor<LandingEnquiry> saved = ArgumentCaptor.forClass(LandingEnquiry.class);
        verify(enquiryRepo).save(saved.capture());
        assertThat(saved.getValue().getStudentsEstimate()).isEqualTo(2250);
    }

    @Test
    void mediaUploadRunsMalwareScannerAndFailsClosedOnInfectedOrPending() {
        LandingMediaRepository mediaRepo = mock(LandingMediaRepository.class);
        when(mediaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        byte[] pngHeader = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "hero.png", "image/png", pngHeader);
        PlatformPrincipal owner = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PRODUCT_OWNER, "owner@edu360.test", false);

        // Infected upload must be rejected
        LandingMediaService infectedService = new LandingMediaService(
                mediaRepo, bytes -> FileScanner.ScanResult.INFECTED, mock(PlatformAuditWriter.class), false);
        assertThatThrownBy(() -> infectedService.upload(file, "IMAGE", owner))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("security scanner");

        // Pending media must fail closed when allowPendingDownload=false
        UUID mediaId = UUID.randomUUID();
        LandingMedia pendingMedia = LandingMedia.builder()
                .mediaId(mediaId)
                .mediaKind("IMAGE")
                .fileName("hero.png")
                .contentType("image/png")
                .byteSize(12)
                .sha256("abc")
                .scanStatus("PENDING")
                .status("ACTIVE")
                .content(pngHeader)
                .createdBy(owner.platformUserId())
                .build();
        when(mediaRepo.findById(mediaId)).thenReturn(Optional.of(pendingMedia));

        assertThatThrownBy(() -> infectedService.loadPublicMedia(mediaId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("awaiting security scanning");

        // Clean media loads normally
        pendingMedia.setScanStatus("CLEAN");
        assertThat(infectedService.loadPublicMedia(mediaId).getContent()).isEqualTo(pngHeader);
    }

    @Test
    void cmsControllerEnforcesPlatformPermission() {
        PlatformAccessLookup lookup = mock(PlatformAccessLookup.class);
        PlatformPermissionService permService = new PlatformPermissionService(lookup, mock(PlatformAuditWriter.class));
        PlatformPrincipal admin = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PLATFORM_ADMIN, "admin@edu360.test", false);
        PlatformPrincipal billing = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.BILLING_MANAGER, "billing@edu360.test", false);

        permService.authorize(admin, "LANDING_CMS_MANAGE");
        assertThatThrownBy(() -> permService.authorize(billing, "LANDING_CMS_MANAGE"))
                .isInstanceOf(UnauthorizedException.class);
    }

    private static LandingCmsService createService(
            TenantRepository tenantRepo, PlatformSubscriptionService subService) {
        return new LandingCmsService(
                tenantRepo,
                mock(LandingPageConfigRepository.class),
                mock(LandingTestimonialRepository.class),
                mock(LandingEnquiryRepository.class),
                mock(CommercialCatalogItemRepository.class),
                subService,
                new PlatformConcurrency(),
                mock(PlatformAuditWriter.class)
        );
    }
}
