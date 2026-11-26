package com.edu360.api.governance.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.files.FileUploadResponse;
import com.edu360.api.governance.service.DisciplinaryIncidentService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/governance/disciplinary-incidents",
        "/api/tenants/{tenantId}/governance/disciplinary-incidents"})
@RequiredArgsConstructor
public class DisciplinaryIncidentController {
    private final DisciplinaryIncidentService incidents;

    public record CreateRequest(@NotNull UUID studentId, @NotNull LocalDate incidentDate,
            @NotBlank String severity, @NotBlank @Size(max=5000) String description) { }
    public record UpdateRequest(@NotBlank String status, @Size(max=5000) String actionTaken,
            @Size(max=5000) String notes) { }
    public record ActionRequest(@NotBlank String type, @NotBlank @Size(max=5000) String notes,
            UUID noticeFileId, UUID minutesFileId, @Size(max=2000) String penalty) { }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DisciplinaryIncidentService.Incident create(@PathVariable UUID tenantId,
            @Valid @RequestBody CreateRequest body, Authentication auth) {
        return incidents.create(principal(auth, tenantId), body.studentId(), body.incidentDate(),
                body.severity(), body.description());
    }

    @GetMapping
    public List<DisciplinaryIncidentService.Incident> list(@PathVariable UUID tenantId,
            @RequestParam UUID institutionId, Authentication auth) {
        return incidents.list(principal(auth, tenantId), institutionId);
    }

    @GetMapping("/{incidentId}")
    public DisciplinaryIncidentService.Incident get(@PathVariable UUID tenantId,
            @PathVariable UUID incidentId, Authentication auth) {
        return incidents.get(principal(auth, tenantId), incidentId);
    }

    @GetMapping("/{incidentId}/actions")
    public List<DisciplinaryIncidentService.Action> actions(@PathVariable UUID tenantId,
            @PathVariable UUID incidentId, Authentication auth) {
        return incidents.actions(principal(auth, tenantId), incidentId);
    }

    @PatchMapping("/{incidentId}")
    public DisciplinaryIncidentService.Incident update(@PathVariable UUID tenantId,
            @PathVariable UUID incidentId, @Valid @RequestBody UpdateRequest body, Authentication auth) {
        return incidents.update(principal(auth, tenantId), incidentId,
                body.actionTaken(), body.status(), body.notes());
    }

    @PostMapping(value = "/{incidentId}/evidence", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponse uploadEvidence(@PathVariable UUID tenantId,
            @PathVariable UUID incidentId, @RequestParam("file") MultipartFile file, Authentication auth) {
        return incidents.uploadEvidence(principal(auth, tenantId), incidentId, file);
    }

    @GetMapping("/{incidentId}/evidence")
    public List<DisciplinaryIncidentService.Evidence> evidence(@PathVariable UUID tenantId,
            @PathVariable UUID incidentId, Authentication auth) {
        return incidents.evidence(principal(auth, tenantId), incidentId);
    }

    @PostMapping("/{incidentId}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    public void action(@PathVariable UUID tenantId, @PathVariable UUID incidentId,
            @Valid @RequestBody ActionRequest body, Authentication auth) {
        incidents.action(principal(auth, tenantId), incidentId, body.type(), body.notes(),
                body.noticeFileId(), body.minutesFileId(), body.penalty());
    }

    private static TenantPrincipal principal(Authentication auth, UUID tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(p.tenantId())) throw new ResourceNotFoundException("Disciplinary incident not found.");
        return p;
    }
}
