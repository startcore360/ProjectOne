package com.edu360.api.t10;

import com.edu360.api.config.TenantPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/v1/tenants/{tenantId}/notice-bulletins", "/api/tenants/{tenantId}/notice-bulletins"})
public class NoticeBulletinController {
    private final T10DomainService service;
    @GetMapping public List<Map<String,Object>> list(@PathVariable UUID tenantId,@RequestParam UUID institutionId,Authentication auth) {
        return service.listNotices(tenantId, principal(auth,tenantId), institutionId);
    }
    @PostMapping public Map<String,Object> publish(@PathVariable UUID tenantId,@RequestBody NoticeRequest request,Authentication auth) {
        return service.publishNotice(tenantId, principal(auth,tenantId), request);
    }
    @PostMapping("/{noticeId}/acknowledgment") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acknowledge(@PathVariable UUID tenantId,@PathVariable UUID noticeId,@RequestParam UUID institutionId,Authentication auth) {
        service.acknowledgeNotice(tenantId,principal(auth,tenantId),institutionId,noticeId);
    }
    private TenantPrincipal principal(Authentication a,UUID t) {
        if(a==null || !(a.getPrincipal() instanceof TenantPrincipal p)) throw new AccessDeniedException("Tenant authentication required");
        if(!t.equals(p.tenantId())) throw new AccessDeniedException("Cross-tenant access forbidden");
        return p;
    }
    public record NoticeRequest(UUID institutionId,String title,String body,List<String> targetRoles,String targetKind,UUID targetId,boolean urgent,boolean requiresAcknowledgment) {}
}
