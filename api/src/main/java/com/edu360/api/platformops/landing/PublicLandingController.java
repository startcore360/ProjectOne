package com.edu360.api.platformops.landing;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.platformops.landing.dto.PublicEnquiryRequest;
import com.edu360.api.platformops.landing.dto.PublicEnquiryResponse;
import com.edu360.api.platformops.landing.dto.PublicInstitutionItem;
import com.edu360.api.platformops.landing.dto.PublicLandingResponse;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/public", "/api/public"})
@RequiredArgsConstructor
public class PublicLandingController {
    private final LandingCmsService landingService;
    private final LandingMediaService mediaService;
    private final PublicEnquiryRateLimiter rateLimiter;

    @GetMapping("/institutions")
    @Operation(summary = "List active/trial institutions opted into the public sign-in directory")
    public ResponseEntity<List<PublicInstitutionItem>> listInstitutions(
            @RequestParam(value = "q", required = false) String query) {
        return ResponseEntity.ok(landingService.listPublicInstitutions(query));
    }

    @GetMapping("/landing")
    @Operation(summary = "Fetch public landing page configuration, published testimonials, and module catalog")
    public ResponseEntity<PublicLandingResponse> getLanding() {
        return ResponseEntity.ok(landingService.getPublicLanding());
    }

    @GetMapping("/landing/media/{mediaId}")
    @Operation(summary = "Stream a scanned public landing image or video asset")
    public ResponseEntity<byte[]> getMedia(@PathVariable UUID mediaId) {
        LandingMedia media = mediaService.loadPublicMedia(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(media.getContent());
    }

    @PostMapping("/enquiries")
    @Operation(summary = "Submit a public quotation or demo request")
    public ResponseEntity<PublicEnquiryResponse> submitEnquiry(
            @Valid @RequestBody PublicEnquiryRequest request,
            HttpServletRequest httpRequest) {
        String forwarded = httpRequest.getHeader("X-Forwarded-For");
        String clientIp = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : httpRequest.getRemoteAddr();
        rateLimiter.checkAllowed(clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(landingService.submitPublicEnquiry(request));
    }
}
