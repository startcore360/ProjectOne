package com.edu360.api.student;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class StudentDirectoryController {
    private final StudentDirectoryService service;

    @GetMapping({"/v1/tenants/{tenantId}/students","/api/tenants/{tenantId}/students"})
    public StudentDirectoryResponse list(@PathVariable UUID tenantId, @RequestParam(required=false) UUID institutionId,
        @RequestParam(required=false) UUID departmentId,@RequestParam(required=false) UUID programId,
        @RequestParam(required=false) UUID batchId,@RequestParam(required=false) UUID sectionId,
        @RequestParam(required=false) String status,@RequestParam(required=false) String q,@RequestParam(required=false) String cursor,Authentication auth){
        return service.list(tenantId,principal(auth,tenantId),institutionId,departmentId,programId,batchId,sectionId,status,q,cursor);
    }
    @GetMapping({"/v1/tenants/{tenantId}/students/{studentId}/profile","/api/tenants/{tenantId}/students/{studentId}/profile"})
    public Map<String,Object> get(@PathVariable UUID tenantId,@PathVariable UUID studentId,Authentication auth){return service.get(tenantId,principal(auth,tenantId),studentId);}
    @PatchMapping({"/v1/tenants/{tenantId}/students/{studentId}/profile","/api/tenants/{tenantId}/students/{studentId}/profile"})
    public Map<String,Object> update(@PathVariable UUID tenantId,@PathVariable UUID studentId,@Valid @RequestBody StudentProfileUpdateRequest request,Authentication auth){return service.update(tenantId,principal(auth,tenantId),studentId,request);}
    @GetMapping({"/v1/tenants/{tenantId}/students/{studentId}/notes","/api/tenants/{tenantId}/students/{studentId}/notes"})
    public List<Map<String,Object>> notes(@PathVariable UUID tenantId,@PathVariable UUID studentId,Authentication auth){return service.notes(tenantId,principal(auth,tenantId),studentId);}
    @PostMapping({"/v1/tenants/{tenantId}/students/{studentId}/notes","/api/tenants/{tenantId}/students/{studentId}/notes"})
    public ResponseEntity<Map<String,Object>> addNote(@PathVariable UUID tenantId,@PathVariable UUID studentId,@Valid @RequestBody StudentNoteRequest request,Authentication auth){return ResponseEntity.status(HttpStatus.CREATED).body(service.addNote(tenantId,principal(auth,tenantId),studentId,request));}
    @GetMapping({"/v1/tenants/{tenantId}/students/{studentId}/legal-holds","/api/tenants/{tenantId}/students/{studentId}/legal-holds"})
    public List<Map<String,Object>> holds(@PathVariable UUID tenantId,@PathVariable UUID studentId,Authentication auth){return service.legalHolds(tenantId,principal(auth,tenantId),studentId);}
    private TenantPrincipal principal(Authentication a,UUID tenantId){if(a==null||!(a.getPrincipal() instanceof TenantPrincipal p)||!tenantId.equals(p.tenantId()))throw new UnauthorizedException("Authenticated tenant principal required.");return p;}
}