package com.edu360.api.platformops.landing;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platformops.PlatformConcurrency;
import com.edu360.api.platformops.landing.dto.LandingConfigUpdateRequest;
import com.edu360.api.platformops.landing.dto.LandingTestimonialReorderRequest;
import com.edu360.api.platformops.landing.dto.LandingTestimonialRequest;
import com.edu360.api.platformops.landing.dto.LandingTestimonialResponse;
import com.edu360.api.platformops.landing.dto.LandingEnquiryStatusRequest;
import com.edu360.api.platformops.landing.dto.PlatformLandingCmsResponse;
import com.edu360.api.platformops.landing.dto.PlatformLandingEnquiryResponse;
import com.edu360.api.platformops.landing.dto.PublicCatalogModuleItem;
import com.edu360.api.platformops.landing.dto.PublicEnquiryRequest;
import com.edu360.api.platformops.landing.dto.PublicEnquiryResponse;
import com.edu360.api.platformops.landing.dto.PublicInstitutionItem;
import com.edu360.api.platformops.landing.dto.PublicLandingResponse;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platformops.subscription.CommercialCatalogItemRepository;
import com.edu360.api.platformops.subscription.PlatformSubscriptionService;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteRequest;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteResponse;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LandingCmsService {
    private static final List<TenantState> DIRECTORY_STATES = List.of(TenantState.ACTIVE, TenantState.TRIAL);

    private final TenantRepository tenantRepository;
    private final LandingPageConfigRepository configRepository;
    private final LandingTestimonialRepository testimonialRepository;
    private final LandingEnquiryRepository enquiryRepository;
    private final CommercialCatalogItemRepository catalogRepository;
    private final PlatformSubscriptionService subscriptionService;
    private final PlatformConcurrency concurrency;
    private final PlatformAuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public LandingCmsService(
            TenantRepository tenantRepository,
            LandingPageConfigRepository configRepository,
            LandingTestimonialRepository testimonialRepository,
            LandingEnquiryRepository enquiryRepository,
            CommercialCatalogItemRepository catalogRepository,
            PlatformSubscriptionService subscriptionService,
            PlatformConcurrency concurrency,
            PlatformAuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.configRepository = configRepository;
        this.testimonialRepository = testimonialRepository;
        this.enquiryRepository = enquiryRepository;
        this.catalogRepository = catalogRepository;
        this.subscriptionService = subscriptionService;
        this.concurrency = concurrency;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public List<PublicInstitutionItem> listPublicInstitutions(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return tenantRepository.findByStateInAndListedInDirectoryTrueOrderByTenantNameAsc(DIRECTORY_STATES)
                .stream()
                .filter(t -> needle.isEmpty()
                        || t.getTenantName().toLowerCase(Locale.ROOT).contains(needle)
                        || t.getSubdomain().toLowerCase(Locale.ROOT).contains(needle))
                .map(t -> new PublicInstitutionItem(t.getTenantName(), t.getSubdomain(), t.getTenantType()))
                .toList();
    }

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public PublicLandingResponse getPublicLanding() {
        LandingPageConfig cfg = getOrDefaultConfig();
        List<LandingTestimonialResponse> testimonials = testimonialRepository
                .findByPublishedTrueOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(LandingTestimonialResponse::from)
                .toList();
        List<PublicCatalogModuleItem> modules = catalogRepository.findByAvailableTrueOrderByItemNameAsc()
                .stream()
                .filter(item -> "MODULE".equals(item.getCategory()) && item.getModuleCode() != null)
                .map(item -> new PublicCatalogModuleItem(
                        item.getModuleCode(),
                        item.getItemName(),
                        item.getDescription()))
                .toList();

        return new PublicLandingResponse(
                cfg.getHeroEyebrow(),
                cfg.getHeroTitle(),
                cfg.getHeroSubtitle(),
                cfg.getHeroNote(),
                cfg.getTrustLabel(),
                cfg.getWhyChooseJson(),
                cfg.getRoleBenefitsJson(),
                cfg.getSolutionsJson(),
                cfg.getStepsJson(),
                cfg.getContactEmail(),
                cfg.getFooterTagline(),
                cfg.getHeroLightMediaId(),
                cfg.getHeroDarkMediaId(),
                cfg.getShowcaseLightMediaId(),
                cfg.getShowcaseDarkMediaId(),
                testimonials,
                modules
        );
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PublicEnquiryResponse submitPublicEnquiry(PublicEnquiryRequest req) {
        List<String> modules = req.selectedModules() == null ? List.of() : req.selectedModules();
        String modulesJson = toJson(modules);
        int billingCycle = req.billingCycleMonths() == null ? 12 : req.billingCycleMonths();
        boolean includeSetup = req.includeSetupFee() == null || req.includeSetupFee();

        String quoteSnapshotJson = null;
        if ("QUOTATION".equals(req.enquiryType())) {
            int students = req.studentsEstimate() > 0 ? req.studentsEstimate() : estimateStudentsFromRange(req.studentRange());
            String tenantType = mapInstitutionTypeToTenantType(req.institutionType());
            try {
                CustomerQuoteResponse quote = subscriptionService.quote(new CustomerQuoteRequest(
                        tenantType,
                        req.collegesCount(),
                        req.schoolsCount(),
                        students,
                        req.staffEstimate(),
                        modules,
                        billingCycle,
                        includeSetup
                ));
                quoteSnapshotJson = toJson(quote);
            } catch (RuntimeException ex) {
                // Keep enquiry submission resilient even if catalog rules are incomplete in dev
                quoteSnapshotJson = toJson(Map.of("note", "Quote snapshot deferred for manual review"));
            }
        }

        UUID enquiryId = UUID.randomUUID();
        Instant now = Instant.now();
        LandingEnquiry saved = enquiryRepository.save(LandingEnquiry.builder()
                .enquiryId(enquiryId)
                .enquiryType(req.enquiryType())
                .institutionType(trimOrNull(req.institutionType()))
                .studentRange(trimOrNull(req.studentRange()))
                .collegesCount(req.collegesCount())
                .schoolsCount(req.schoolsCount())
                .studentsEstimate(req.studentsEstimate())
                .staffEstimate(req.staffEstimate())
                .billingCycleMonths(billingCycle)
                .includeSetupFee(includeSetup)
                .selectedModules(modulesJson)
                .migrationSupport(trimOrNull(req.migrationSupport()))
                .trainingMode(trimOrNull(req.trainingMode()))
                .contactName(req.contactName().trim())
                .contactEmail(req.contactEmail().trim())
                .contactPhone(trimOrNull(req.contactPhone()))
                .institutionName(req.institutionName().trim())
                .notes(trimOrNull(req.notes()))
                .quoteSnapshotJson(quoteSnapshotJson)
                .status("NEW")
                .createdAt(now)
                .updatedAt(now)
                .build());

        String message = "QUOTATION".equals(saved.getEnquiryType())
                ? "Your quotation request has been received. Our EDU 360 team will review your requirements and share a tailored proposal."
                : "Your demo request has been received. Our EDU 360 team will contact you shortly to schedule a walkthrough.";

        return new PublicEnquiryResponse(saved.getEnquiryId(), saved.getEnquiryType(), saved.getStatus(), message, saved.getCreatedAt());
    }

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public PlatformLandingCmsResponse getCmsOverview() {
        LandingPageConfig cfg = getOrDefaultConfig();
        PlatformLandingCmsResponse.ConfigDto cfgDto = new PlatformLandingCmsResponse.ConfigDto(
                cfg.getConfigId(),
                cfg.getHeroEyebrow(),
                cfg.getHeroTitle(),
                cfg.getHeroSubtitle(),
                cfg.getHeroNote(),
                cfg.getTrustLabel(),
                cfg.getWhyChooseJson(),
                cfg.getRoleBenefitsJson(),
                cfg.getSolutionsJson(),
                cfg.getStepsJson(),
                cfg.getContactEmail(),
                cfg.getFooterTagline(),
                cfg.getHeroLightMediaId(),
                cfg.getHeroDarkMediaId(),
                cfg.getShowcaseLightMediaId(),
                cfg.getShowcaseDarkMediaId(),
                cfg.getRowVersion()
        );
        List<LandingTestimonialResponse> testimonials = testimonialRepository
                .findAllByOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(LandingTestimonialResponse::from)
                .toList();
        List<PlatformLandingCmsResponse.DirectoryTenantItem> tenants = tenantRepository
                .findAllByOrderByTenantNameAsc()
                .stream()
                .map(t -> new PlatformLandingCmsResponse.DirectoryTenantItem(
                        t.getTenantId(),
                        t.getTenantName(),
                        t.getSubdomain(),
                        t.getTenantType(),
                        t.getState().name(),
                        t.isListedInDirectory()))
                .toList();
        return new PlatformLandingCmsResponse(cfgDto, testimonials, tenants);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformLandingCmsResponse.ConfigDto updateConfig(
            LandingConfigUpdateRequest req, String ifMatch, PlatformPrincipal actor) {
        LandingPageConfig cfg = getOrDefaultConfig();
        concurrency.requireIfMatch(ifMatch, cfg.getRowVersion());

        validateJson(req.whyChooseJson(), "whyChooseJson");
        validateJson(req.roleBenefitsJson(), "roleBenefitsJson");
        validateJson(req.solutionsJson(), "solutionsJson");
        validateJson(req.stepsJson(), "stepsJson");

        cfg.setHeroEyebrow(req.heroEyebrow().trim());
        cfg.setHeroTitle(req.heroTitle().trim());
        cfg.setHeroSubtitle(req.heroSubtitle().trim());
        cfg.setHeroNote(req.heroNote().trim());
        cfg.setTrustLabel(req.trustLabel().trim());
        cfg.setWhyChooseJson(req.whyChooseJson());
        cfg.setRoleBenefitsJson(req.roleBenefitsJson());
        cfg.setSolutionsJson(req.solutionsJson());
        cfg.setStepsJson(req.stepsJson());
        cfg.setContactEmail(req.contactEmail().trim());
        cfg.setFooterTagline(req.footerTagline().trim());
        cfg.setHeroLightMediaId(req.heroLightMediaId());
        cfg.setHeroDarkMediaId(req.heroDarkMediaId());
        cfg.setShowcaseLightMediaId(req.showcaseLightMediaId());
        cfg.setShowcaseDarkMediaId(req.showcaseDarkMediaId());
        cfg.setUpdatedBy(actor.platformUserId());
        cfg.setRowVersion(cfg.getRowVersion() + 1);

        LandingPageConfig saved = configRepository.save(cfg);
        auditWriter.record(actor, "CONFIG_CHANGE", "LANDING_CMS_MANAGE", "LANDING_PAGE_CONFIG", saved.getConfigId());

        return new PlatformLandingCmsResponse.ConfigDto(
                saved.getConfigId(), saved.getHeroEyebrow(), saved.getHeroTitle(),
                saved.getHeroSubtitle(), saved.getHeroNote(), saved.getTrustLabel(),
                saved.getWhyChooseJson(), saved.getRoleBenefitsJson(), saved.getSolutionsJson(),
                saved.getStepsJson(), saved.getContactEmail(), saved.getFooterTagline(),
                saved.getHeroLightMediaId(), saved.getHeroDarkMediaId(),
                saved.getShowcaseLightMediaId(), saved.getShowcaseDarkMediaId(), saved.getRowVersion()
        );
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public LandingTestimonialResponse createTestimonial(LandingTestimonialRequest req, PlatformPrincipal actor) {
        LandingTestimonial saved = testimonialRepository.save(LandingTestimonial.builder()
                .testimonialId(UUID.randomUUID())
                .kind(req.kind())
                .authorName(req.authorName().trim())
                .authorRole(req.authorRole().trim())
                .institutionName(req.institutionName().trim())
                .quoteText(req.quoteText().trim())
                .videoUrl(trimOrNull(req.videoUrl()))
                .videoMediaId(req.videoMediaId())
                .posterMediaId(req.posterMediaId())
                .displayOrder(req.displayOrder())
                .published(req.published())
                .createdBy(actor.platformUserId())
                .updatedBy(actor.platformUserId())
                .build());
        auditWriter.record(actor, "CREATE", "LANDING_CMS_MANAGE", "LANDING_TESTIMONIAL", saved.getTestimonialId());
        return LandingTestimonialResponse.from(saved);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public LandingTestimonialResponse updateTestimonial(
            UUID testimonialId, LandingTestimonialRequest req, String ifMatch, PlatformPrincipal actor) {
        LandingTestimonial entity = testimonialRepository.findById(testimonialId)
                .orElseThrow(() -> new ResourceNotFoundException("Testimonial not found."));
        concurrency.requireIfMatch(ifMatch, entity.getRowVersion());

        entity.setKind(req.kind());
        entity.setAuthorName(req.authorName().trim());
        entity.setAuthorRole(req.authorRole().trim());
        entity.setInstitutionName(req.institutionName().trim());
        entity.setQuoteText(req.quoteText().trim());
        entity.setVideoUrl(trimOrNull(req.videoUrl()));
        entity.setVideoMediaId(req.videoMediaId());
        entity.setPosterMediaId(req.posterMediaId());
        entity.setDisplayOrder(req.displayOrder());
        entity.setPublished(req.published());
        entity.setUpdatedBy(actor.platformUserId());
        entity.setRowVersion(entity.getRowVersion() + 1);

        LandingTestimonial saved = testimonialRepository.save(entity);
        auditWriter.record(actor, "UPDATE", "LANDING_CMS_MANAGE", "LANDING_TESTIMONIAL", saved.getTestimonialId());
        return LandingTestimonialResponse.from(saved);
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public void deleteTestimonial(UUID testimonialId, PlatformPrincipal actor) {
        LandingTestimonial entity = testimonialRepository.findById(testimonialId)
                .orElseThrow(() -> new ResourceNotFoundException("Testimonial not found."));
        testimonialRepository.delete(entity);
        auditWriter.record(actor, null, "DELETE", "DELETE", "LANDING_CMS_MANAGE",
                "LANDING_TESTIMONIAL", testimonialId, PlatformAuditWriter.SUCCESS, Map.of());
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public List<LandingTestimonialResponse> reorderTestimonials(
            LandingTestimonialReorderRequest req, PlatformPrincipal actor) {
        List<UUID> ids = req.orderedIds();
        for (int i = 0; i < ids.size(); i++) {
            UUID id = ids.get(i);
            int order = i;
            testimonialRepository.findById(id).ifPresent(entity -> {
                entity.setDisplayOrder(order);
                entity.setUpdatedBy(actor.platformUserId());
                entity.setRowVersion(entity.getRowVersion() + 1);
                testimonialRepository.save(entity);
            });
        }
        return testimonialRepository.findAllByOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(LandingTestimonialResponse::from)
                .toList();
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformLandingCmsResponse.DirectoryTenantItem setTenantDirectoryListing(
            UUID tenantId, boolean listedInDirectory, PlatformPrincipal actor) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found."));
        tenant.setListedInDirectory(listedInDirectory);
        tenant.setUpdatedBy(actor.platformUserId());
        tenant.setRowVersion(tenant.getRowVersion() + 1);
        Tenant saved = tenantRepository.save(tenant);

        auditWriter.record(actor, saved.getTenantId(), "CONFIG_CHANGE", "LANDING_CMS_MANAGE", "TENANT", saved.getTenantId());

        return new PlatformLandingCmsResponse.DirectoryTenantItem(
                saved.getTenantId(),
                saved.getTenantName(),
                saved.getSubdomain(),
                saved.getTenantType(),
                saved.getState().name(),
                saved.isListedInDirectory()
        );
    }

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public List<PlatformLandingEnquiryResponse> listEnquiries(String enquiryType, String status) {
        List<LandingEnquiry> list;
        boolean hasType = enquiryType != null && !enquiryType.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        if (hasType && hasStatus) {
            list = enquiryRepository.findByEnquiryTypeAndStatusOrderByCreatedAtDesc(
                    enquiryType.trim().toUpperCase(), status.trim().toUpperCase());
        } else if (hasType) {
            list = enquiryRepository.findByEnquiryTypeOrderByCreatedAtDesc(enquiryType.trim().toUpperCase());
        } else if (hasStatus) {
            list = enquiryRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase());
        } else {
            list = enquiryRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(PlatformLandingEnquiryResponse::from).toList();
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformLandingEnquiryResponse updateEnquiryStatus(
            UUID enquiryId, LandingEnquiryStatusRequest req, String ifMatch, PlatformPrincipal actor) {
        LandingEnquiry entity = enquiryRepository.findById(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry not found."));
        concurrency.requireIfMatch(ifMatch, entity.getRowVersion());

        entity.setStatus(req.status());
        entity.setInternalNotes(trimOrNull(req.internalNotes()));
        entity.setHandledBy(actor.platformUserId());
        entity.setRowVersion(entity.getRowVersion() + 1);
        LandingEnquiry saved = enquiryRepository.save(entity);

        auditWriter.record(actor, "UPDATE", "LANDING_CMS_MANAGE", "LANDING_ENQUIRY", saved.getEnquiryId());
        return PlatformLandingEnquiryResponse.from(saved);
    }

    private LandingPageConfig getOrDefaultConfig() {
        return configRepository.findById(LandingPageConfig.DEFAULT_CONFIG_ID)
                .orElseGet(() -> configRepository.findAll().stream().findFirst()
                        .orElseGet(this::buildFallbackConfig));
    }

    private LandingPageConfig buildFallbackConfig() {
        return LandingPageConfig.builder()
                .configId(LandingPageConfig.DEFAULT_CONFIG_ID)
                .heroEyebrow("A connected education management platform")
                .heroTitle("Bring your entire institution into sync.")
                .heroSubtitle("From academic administration, governance, and fee management to parent communication and student workflows—EDU 360 connects the work that keeps education moving.")
                .heroNote("Designed for Schools · Colleges · Universities")
                .trustLabel("One connected platform for every stakeholder in your institution")
                .whyChooseJson("[]")
                .roleBenefitsJson("[]")
                .solutionsJson("[]")
                .stepsJson("[]")
                .contactEmail("hello@edu360.example")
                .footerTagline("One Platform. Every Institution. One Connected Ecosystem.")
                .build();
    }

    private void validateJson(String json, String fieldName) {
        try {
            objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(fieldName + " must be valid JSON.");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize JSON.", ex);
        }
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static int estimateStudentsFromRange(String range) {
        if (range == null) return 1000;
        String r = range.trim().toLowerCase(Locale.ROOT);
        if (r.contains("below 500")) return 400;
        if (r.contains("500")) return 1000;
        if (r.contains("1,501") || r.contains("1501")) return 2250;
        if (r.contains("3,001") || r.contains("3001")) return 4000;
        if (r.contains("5,001") || r.contains("5001")) return 7500;
        if (r.contains("above 10,000") || r.contains("10000")) return 12000;
        return 1000;
    }

    private static String mapInstitutionTypeToTenantType(String institutionType) {
        if (institutionType == null) return "STANDALONE_COLLEGE";
        String lower = institutionType.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("school")) return "STANDALONE_SCHOOL";
        if (lower.contains("university") || lower.contains("multi")) return "UNIVERSITY";
        return "STANDALONE_COLLEGE";
    }
}
