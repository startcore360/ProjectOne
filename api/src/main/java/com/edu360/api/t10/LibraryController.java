package com.edu360.api.t10;

import com.edu360.api.config.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/v1/tenants/{tenantId}/library", "/api/tenants/{tenantId}/library"})
public class LibraryController {
    private final T10DomainService service;
    @GetMapping("/catalog") public List<Map<String,Object>> catalog(@PathVariable UUID tenantId,@RequestParam UUID institutionId,@RequestParam(required=false) String q,Authentication auth) {
        return service.libraryCatalog(tenantId,principal(auth,tenantId),institutionId,q);
    }
    @PostMapping("/catalog") public Map<String,Object> addBook(@PathVariable UUID tenantId,@RequestBody BookRequest r,Authentication auth) {
        return service.addBook(tenantId,principal(auth,tenantId),r);
    }
    @PostMapping("/circulations") public Map<String,Object> issue(@PathVariable UUID tenantId,@RequestBody IssueRequest r,Authentication auth) {
        return service.issueBook(tenantId,principal(auth,tenantId),r);
    }
    @PostMapping("/circulations/{id}/return") public Map<String,Object> returnBook(@PathVariable UUID tenantId,@PathVariable UUID id,@RequestParam UUID institutionId,Authentication auth) {
        return service.returnBook(tenantId,principal(auth,tenantId),institutionId,id);
    }
    @PostMapping("/reservations") public Map<String,Object> reserve(@PathVariable UUID tenantId,@RequestBody ReservationRequest r,Authentication auth) {
        return service.reserveBook(tenantId,principal(auth,tenantId),r);
    }
    @GetMapping("/fine-policy") public Map<String,Object> finePolicy(@PathVariable UUID tenantId,@RequestParam UUID institutionId,Authentication auth) {
        return service.finePolicy(tenantId,principal(auth,tenantId),institutionId);
    }
    @PutMapping("/fine-policy") public Map<String,Object> updateFinePolicy(@PathVariable UUID tenantId,@RequestBody FinePolicyRequest r,Authentication auth) {
        return service.updateFinePolicy(tenantId,principal(auth,tenantId),r);
    }
    @PostMapping("/fines/{fineId}/pay") public Map<String,Object> payFine(@PathVariable UUID tenantId,@PathVariable UUID fineId,@RequestParam UUID institutionId,Authentication auth) {
        return service.payFine(tenantId,principal(auth,tenantId),institutionId,fineId);
    }
    private TenantPrincipal principal(Authentication a,UUID t) {
        if(a==null || !(a.getPrincipal() instanceof TenantPrincipal p)) throw new AccessDeniedException("Tenant authentication required");
        if(!t.equals(p.tenantId())) throw new AccessDeniedException("Cross-tenant access forbidden");
        return p;
    }
    public record BookRequest(UUID institutionId,String isbn,String title,String author,String category,int copies) {}
    public record IssueRequest(UUID institutionId,UUID bookId,UUID copyId,UUID borrowerUserId,String borrowerRole,LocalDate dueDate) {}
    public record ReservationRequest(UUID institutionId,UUID bookId,UUID borrowerUserId) {}
    public record FinePolicyRequest(UUID institutionId,int gracePeriodDays,String calculationMode,BigDecimal dailyAmount,String dayBasis,BigDecimal fineCap,List<Integer> workingDays) {}
}
