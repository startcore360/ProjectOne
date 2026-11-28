package com.edu360.api.academic.controller;

import com.edu360.api.academic.ExaminationService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/academic/exams", "/v1/tenants/{tenantId}/academic/exams"})
public class ExaminationController {
    private final ExaminationService service;
    public ExaminationController(ExaminationService service) { this.service = service; }

    @PostMapping("/cycles")
    public ResponseEntity<Map<String, Object>> createCycle(@PathVariable UUID tenantId,
            @RequestBody ExaminationService.ExamCycleRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCycle(tenantId, principal(auth, tenantId), request));
    }

    @PostMapping("/schedules")
    public ResponseEntity<Map<String, Object>> schedule(@PathVariable UUID tenantId,
            @RequestBody ExaminationService.ExamScheduleRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.schedule(tenantId, principal(auth, tenantId), request));
    }

    @GetMapping("/cycles/{cycleId}/schedules")
    public List<Map<String, Object>> schedules(@PathVariable UUID tenantId, @PathVariable UUID cycleId, Authentication auth) {
        return service.schedules(tenantId, principal(auth, tenantId), cycleId);
    }

    @PostMapping("/schedules/{scheduleId}/invigilators/{staffUserId}")
    public ResponseEntity<Void> invigilator(@PathVariable UUID tenantId, @PathVariable UUID scheduleId,
            @PathVariable UUID staffUserId, Authentication auth) {
        service.assignInvigilator(tenantId, principal(auth, tenantId), scheduleId, staffUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/marks")
    public ResponseEntity<Map<String, Object>> enterMarks(@PathVariable UUID tenantId,
            @RequestBody ExaminationService.MarksEntryRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.enterMarks(tenantId, principal(auth, tenantId), request));
    }

    @PostMapping("/marks/{markId}/course-teacher-approve")
    public Map<String, Object> courseTeacherApprove(@PathVariable UUID tenantId,
            @PathVariable UUID markId, Authentication auth) {
        return service.courseTeacherApprove(tenantId, principal(auth, tenantId), markId);
    }

    @PostMapping("/marks/{markId}/external-moderation")
    public Map<String, Object> externalModerate(@PathVariable UUID tenantId, @PathVariable UUID markId,
            @RequestBody ExternalModerationRequest request, Authentication auth) {
        return service.externalModerate(tenantId, principal(auth, tenantId), markId,
                request.moderatedMarks(), request.decision(), request.comment());
    }

    private static TenantPrincipal principal(Authentication auth, UUID tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) throw new UnauthorizedException("Tenant authentication is required.");
        if (!tenantId.equals(p.tenantId())) throw new ResourceNotFoundException("Exam resource not found.");
        return p;
    }
    public record ExternalModerationRequest(BigDecimal moderatedMarks, String decision, String comment) {}
}
