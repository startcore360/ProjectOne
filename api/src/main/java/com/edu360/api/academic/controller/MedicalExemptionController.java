package com.edu360.api.academic.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.edu360.api.academic.service.MedicalExemptionService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/attendance/medical-exemptions","/v1/tenants/{tenantId}/attendance/medical-exemptions"})
@RequiredArgsConstructor
public class MedicalExemptionController {
    private final MedicalExemptionService service;

    @GetMapping("/my-enrollments")
    public List<Map<String,Object>> myEnrollments(@PathVariable UUID tenantId,Authentication auth){
        return service.myEnrollments(tenantId,principal(auth,tenantId));
    }

    @PostMapping
    public ResponseEntity<Map<String,Object>> request(@PathVariable UUID tenantId,@Valid @RequestBody Request body,Authentication auth){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(tenantId,principal(auth,tenantId),body.institutionId(),
                body.enrollmentId(),body.startDate(),body.endDate(),body.documentRef()));
    }

    @PostMapping("/{exemptionId}/decision")
    public Map<String,Object> decide(@PathVariable UUID tenantId,@PathVariable UUID exemptionId,
            @Valid @RequestBody Decision body,Authentication auth){
        return service.decide(tenantId,principal(auth,tenantId),body.institutionId(),exemptionId,body.decision(),body.note());
    }

    @GetMapping
    public List<Map<String,Object>> list(@PathVariable UUID tenantId,@RequestParam UUID institutionId,
            @RequestParam(required=false) UUID enrollmentId,@RequestParam(required=false) UUID departmentId,Authentication auth){
        return service.list(tenantId,principal(auth,tenantId),institutionId,enrollmentId,departmentId);
    }

    public record Request(@NotNull UUID institutionId,@NotNull UUID enrollmentId,@NotNull LocalDate startDate,
            @NotNull LocalDate endDate,@NotNull UUID documentRef){}
    public record Decision(@NotNull UUID institutionId,@NotBlank @Pattern(regexp="APPROVED|REJECTED") String decision,
            @Size(max=1000) String note){}

    private static TenantPrincipal principal(Authentication auth,UUID tenantId){
        if(auth==null||!(auth.getPrincipal() instanceof TenantPrincipal p))throw new UnauthorizedException("Tenant authentication is required.");
        if(!tenantId.equals(p.tenantId()))throw new ResourceNotFoundException("Tenant not found.");
        return p;
    }
}