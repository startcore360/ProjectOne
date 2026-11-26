package com.edu360.api.governance.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.SupportPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.files.FileUploadResponse;
import com.edu360.api.governance.service.GrievanceService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/governance/grievances",
        "/api/tenants/{tenantId}/governance/grievances"})
@RequiredArgsConstructor
public class GrievanceCaseController {
    private final GrievanceService grievances;

    public record SubmitRequest(@NotNull UUID institutionId, @NotBlank String category,
            @NotBlank @Size(max=10000) String details, UUID accusedUserId, boolean anonymous) { }
    public record AssignRequest(@NotNull UUID investigatorUserId, @Size(max=5000) String notes) { }
    public record HearingRequest(@NotNull OffsetDateTime hearingAt, @NotBlank @Size(max=5000) String notes,
            UUID noticeFileId) { }
    public record MinutesRequest(@NotBlank @Size(max=5000) String notes, UUID minutesFileId) { }
    public record PenaltyRequest(@NotBlank @Size(max=2000) String penalty,
            @NotBlank @Size(max=5000) String notes) { }
    public record ResolveRequest(@NotBlank String outcome, @NotBlank @Size(max=5000) String notes) { }

    @PostMapping("/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public GrievanceService.SubmissionReceipt submit(@PathVariable UUID tenantId,
            @Valid @RequestBody SubmitRequest body, Authentication auth) {
        return grievances.submit(principal(auth, tenantId), body.institutionId(), body.category(),
                body.details(), body.accusedUserId(), body.anonymous());
    }

    @GetMapping("/cases")
    public List<GrievanceService.CaseView> list(@PathVariable UUID tenantId,
            @RequestParam UUID institutionId, Authentication auth) {
        return grievances.list(principal(auth, tenantId), institutionId);
    }

    @GetMapping("/cases/{caseId}")
    public GrievanceService.CaseView get(@PathVariable UUID tenantId,
            @PathVariable UUID caseId, Authentication auth) {
        return grievances.get(principal(auth, tenantId), caseId);
    }

    @PostMapping("/cases/{caseId}/assign")
    public GrievanceService.CaseView assign(@PathVariable UUID tenantId,
            @PathVariable UUID caseId, @Valid @RequestBody AssignRequest body, Authentication auth) {
        return grievances.assign(principal(auth, tenantId), caseId, body.investigatorUserId(), body.notes());
    }

    @PostMapping("/cases/{caseId}/hearing")
    public GrievanceService.CaseView hearing(@PathVariable UUID tenantId,
            @PathVariable UUID caseId, @Valid @RequestBody HearingRequest body, Authentication auth) {
        return grievances.scheduleHearing(principal(auth, tenantId), caseId,
                body.hearingAt(), body.notes(), body.noticeFileId());
    }

    @PostMapping("/cases/{caseId}/minutes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void minutes(@PathVariable UUID tenantId, @PathVariable UUID caseId,
            @Valid @RequestBody MinutesRequest body, Authentication auth) {
        grievances.recordMinutes(principal(auth, tenantId), caseId, body.notes(), body.minutesFileId());
    }

    @PostMapping("/cases/{caseId}/penalty")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void penalty(@PathVariable UUID tenantId, @PathVariable UUID caseId,
            @Valid @RequestBody PenaltyRequest body, Authentication auth) {
        grievances.issuePenalty(principal(auth, tenantId), caseId, body.penalty(), body.notes());
    }

    @PostMapping("/cases/{caseId}/resolve")
    public GrievanceService.CaseView resolve(@PathVariable UUID tenantId,
            @PathVariable UUID caseId, @Valid @RequestBody ResolveRequest body, Authentication auth) {
        return grievances.resolve(principal(auth, tenantId), caseId, body.outcome(), body.notes());
    }

    @PostMapping(value = "/cases/{caseId}/evidence", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponse uploadEvidence(@PathVariable UUID tenantId, @PathVariable UUID caseId,
            @RequestParam("file") MultipartFile file, Authentication auth) {
        return grievances.uploadEvidence(principal(auth, tenantId), caseId, file);
    }

    @GetMapping("/cases/{caseId}/evidence")
    public List<GrievanceService.Evidence> evidence(@PathVariable UUID tenantId,
            @PathVariable UUID caseId, Authentication auth) {
        return grievances.evidence(principal(auth, tenantId), caseId);
    }

    private static TenantPrincipal principal(Authentication auth, UUID tenantId) {
        if (auth != null && auth.getPrincipal() instanceof SupportPrincipal) {
            throw new AccessDeniedException("Support sessions are never permitted to access confidential grievance workflows.");
        }
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(p.tenantId())) throw new ResourceNotFoundException("Grievance case not found.");
        return p;
    }
}
