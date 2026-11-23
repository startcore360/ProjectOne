package com.edu360.api.staff.controller;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.staff.service.StaffAppraisalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class StaffAppraisalController {
    private final StaffAppraisalService service;

    @GetMapping({"/v1/tenants/{tenantId}/institutions/{institutionId}/appraisals/cycles",
                 "/api/tenants/{tenantId}/institutions/{institutionId}/appraisals/cycles"})
    public ResponseEntity<List<Map<String,Object>>> cycles(@PathVariable UUID tenantId,
            @PathVariable UUID institutionId, Authentication authentication) {
        return ResponseEntity.ok(service.cycles(actor(authentication, tenantId), institutionId));
    }

    @PostMapping({"/v1/tenants/{tenantId}/institutions/{institutionId}/appraisals/cycles",
                  "/api/tenants/{tenantId}/institutions/{institutionId}/appraisals/cycles"})
    public ResponseEntity<Map<String,Object>> createCycle(@PathVariable UUID tenantId,
            @PathVariable UUID institutionId, @Valid @RequestBody CreateCycleRequest request,
            Authentication authentication) {
        return ResponseEntity.status(201).body(service.createCycle(actor(authentication, tenantId),
                institutionId, request.academicYearId(), request.name(), request.startDate(), request.endDate()));
    }

    @PostMapping({"/v1/tenants/{tenantId}/appraisals/cycles/{cycleId}/self-evaluation",
                  "/api/tenants/{tenantId}/appraisals/cycles/{cycleId}/self-evaluation"})
    public ResponseEntity<Map<String,Object>> selfEvaluation(@PathVariable UUID tenantId,
            @PathVariable UUID cycleId, @Valid @RequestBody ScoreRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.submitSelfEvaluation(actor(authentication, tenantId),
                cycleId, request.score(), request.commentsJson()));
    }

    @PostMapping({"/v1/tenants/{tenantId}/appraisals/submissions/{submissionId}/peer-reviews",
                  "/api/tenants/{tenantId}/appraisals/submissions/{submissionId}/peer-reviews"})
    public ResponseEntity<Map<String,Object>> peerReview(@PathVariable UUID tenantId,
            @PathVariable UUID submissionId, @Valid @RequestBody PeerReviewRequest request, Authentication authentication) {
        return ResponseEntity.status(201).body(service.addPeerReview(actor(authentication, tenantId), submissionId, request.feedback()));
    }

    @PostMapping({"/v1/tenants/{tenantId}/appraisals/submissions/{submissionId}/hod-score",
                  "/api/tenants/{tenantId}/appraisals/submissions/{submissionId}/hod-score"})
    public ResponseEntity<Map<String,Object>> hodScore(@PathVariable UUID tenantId,
            @PathVariable UUID submissionId, @Valid @RequestBody ScoreRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.scoreAsHod(actor(authentication, tenantId), submissionId, request.score(), request.commentsJson()));
    }

    @PostMapping({"/v1/tenants/{tenantId}/appraisals/submissions/{submissionId}/principal-review",
                  "/api/tenants/{tenantId}/appraisals/submissions/{submissionId}/principal-review"})
    public ResponseEntity<Map<String,Object>> principalReview(@PathVariable UUID tenantId,
            @PathVariable UUID submissionId, @Valid @RequestBody PrincipalReviewRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.principalReview(actor(authentication, tenantId), submissionId,
                request.score(), request.commentsJson(), request.promotionReadiness()));
    }

    private static RbacPrincipal actor(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal p))
            throw new AccessDeniedException("Tenant authentication is required.");
        if (!tenantId.equals(p.tenantId())) throw new AccessDeniedException("Cross-tenant access forbidden.");
        return new RbacPrincipal(p.tenantId(), p.userAccountId());
    }

    public record CreateCycleRequest(@NotNull UUID academicYearId, @NotBlank @Size(max=160) String name,
            @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    public record ScoreRequest(@NotNull BigDecimal score, String commentsJson) {}
    public record PeerReviewRequest(@NotBlank @Size(max=5000) String feedback) {}
    public record PrincipalReviewRequest(@NotNull BigDecimal score, String commentsJson,
            @NotBlank String promotionReadiness) {}
}
