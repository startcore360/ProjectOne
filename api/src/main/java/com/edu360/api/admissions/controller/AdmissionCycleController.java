package com.edu360.api.admissions.controller;

import com.edu360.api.admissions.service.AdmissionService;
import com.edu360.api.config.TenantPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/admissions/cycles","/v1/tenants/{tenantId}/admissions/cycles"})
public class AdmissionCycleController {
    private final AdmissionService service;
    public AdmissionCycleController(AdmissionService service){this.service=service;}
    @GetMapping public List<Map<String,Object>> list(@PathVariable UUID tenantId,@RequestParam(required=false) UUID institutionId,Authentication auth){
        return service.cycles(actor(auth,tenantId),institutionId);
    }
    @PostMapping public ResponseEntity<Map<String,Object>> create(@PathVariable UUID tenantId,@RequestBody Map<String,Object> body,Authentication auth){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCycle(actor(auth,tenantId),body));
    }
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable UUID tenantId,@PathVariable UUID id,@RequestBody Map<String,Object> body,Authentication auth){
        return service.updateCycle(actor(auth,tenantId),id,body);
    }
    static TenantPrincipal actor(Authentication auth,UUID tenantId){
        if(auth==null||!(auth.getPrincipal() instanceof TenantPrincipal p)||!tenantId.equals(p.tenantId()))
            throw new AccessDeniedException("A matching tenant identity is required.");
        return p;
    }
}
