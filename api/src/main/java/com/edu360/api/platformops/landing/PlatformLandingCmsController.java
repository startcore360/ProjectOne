package com.edu360.api.platformops.landing;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.landing.dto.LandingConfigUpdateRequest;
import com.edu360.api.platformops.landing.dto.LandingDirectoryToggleRequest;
import com.edu360.api.platformops.landing.dto.LandingEnquiryStatusRequest;
import com.edu360.api.platformops.landing.dto.LandingMediaUploadResponse;
import com.edu360.api.platformops.landing.dto.LandingTestimonialReorderRequest;
import com.edu360.api.platformops.landing.dto.LandingTestimonialRequest;
import com.edu360.api.platformops.landing.dto.LandingTestimonialResponse;
import com.edu360.api.platformops.landing.dto.PlatformLandingCmsResponse;
import com.edu360.api.platformops.landing.dto.PlatformLandingEnquiryResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/landing", "/api/platform/landing"})
@RequiredArgsConstructor
public class PlatformLandingCmsController {
    private static final String ACTION = "LANDING_CMS_MANAGE";

    private final LandingCmsService landingService;
    private final LandingMediaService mediaService;
    private final PlatformPermissionService permissionService;

    @GetMapping
    @Operation(summary = "Get landing page CMS configuration, testimonials, and directory opt-in status")
    public ResponseEntity<PlatformLandingCmsResponse> getCms(Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, ACTION);
        return ResponseEntity.ok(landingService.getCmsOverview());
    }

    @PutMapping("/config")
    @Operation(summary = "Update landing page CMS content and showcase media")
    public ResponseEntity<PlatformLandingCmsResponse.ConfigDto> updateConfig(
            @Valid @RequestBody LandingConfigUpdateRequest request,
            @RequestHeader("If-Match") String ifMatch,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, ACTION);
        return ResponseEntity.ok(landingService.updateConfig(request, ifMatch, principal));
    }

    @PostMapping("/testimonials")
    @Operation(summary = "Create a text or video testimonial")
    public ResponseEntity<LandingTestimonialResponse> createTestimonial(
            @Valid @RequestBody LandingTestimonialRequest request,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, ACTION);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(landingService.createTestimonial(request, principal));
    }

    @PutMapping("/testimonials/{testimonialId}")
    @Operation(summary = "Update a text or video testimonial")
    public ResponseEntity<LandingTestimonialResponse> updateTestimonial(
            @PathVariable UUID testimonialId,
            @Valid @RequestBody LandingTestimonialRequest request,
            @RequestHeader("If-Match") String ifMatch,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, ACTION);
        return ResponseEntity.ok(landingService.updateTestimonial(testimonialId, request, ifMatch, principal));
    }

    @DeleteMapping("/testimonials/{testimonialId}")
    @Operation(summary = "Delete a testimonial")
    public ResponseEntity<Void> deleteTestimonial(
            @PathVariable UUID testimonialId,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, ACTION);
        landingService.deleteTestimonial(testimonialId, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/testimonials/reorder")
    @Operation(summary = "Reorder testimonials")
    public ResponseEntity<List<LandingTestimonialResponse>> reorderTestimonials(
            @Valid @RequestBody LandingTestimonialReorderRequest request,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, ACTION);
        return ResponseEntity.ok(landingService.reorderTestimonials(request, principal));
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and malware-scan a showcase image or testimonial video")
    public ResponseEntity<LandingMediaUploadResponse> uploadMedia(
            @RequestPart("file") MultipartFile file,
            @RequestParam("mediaKind") String mediaKind,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, ACTION);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.upload(file, mediaKind, principal));
    }

    @PatchMapping("/directory/{tenantId}")
    @Operation(summary = "Toggle whether a tenant appears in the public sign-in directory dropdown")
    public ResponseEntity<PlatformLandingCmsResponse.DirectoryTenantItem> toggleDirectory(
            @PathVariable UUID tenantId,
            @RequestBody LandingDirectoryToggleRequest request,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, ACTION);
        return ResponseEntity.ok(landingService.setTenantDirectoryListing(
                tenantId, request.listedInDirectory(), principal));
    }

    @GetMapping("/enquiries")
    @Operation(summary = "List public demo and quotation enquiries with server-computed quote snapshots")
    public ResponseEntity<List<PlatformLandingEnquiryResponse>> listEnquiries(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, ACTION);
        return ResponseEntity.ok(landingService.listEnquiries(type, status));
    }

    @PatchMapping("/enquiries/{enquiryId}")
    @Operation(summary = "Update enquiry status and internal notes")
    public ResponseEntity<PlatformLandingEnquiryResponse> updateEnquiryStatus(
            @PathVariable UUID enquiryId,
            @Valid @RequestBody LandingEnquiryStatusRequest request,
            @RequestHeader("If-Match") String ifMatch,
            Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        permissionService.authorize(principal, ACTION);
        return ResponseEntity.ok(landingService.updateEnquiryStatus(enquiryId, request, ifMatch, principal));
    }

    private static PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("A platform sign-in is required.");
        }
        return principal;
    }
}
