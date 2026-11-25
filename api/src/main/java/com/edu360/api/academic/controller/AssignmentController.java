package com.edu360.api.academic.controller;

import com.edu360.api.academic.AssignmentService;
import com.edu360.api.academic.CreateAssignmentRequest;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/academic/assignments", "/v1/tenants/{tenantId}/academic/assignments"})
public class AssignmentController {
    private final AssignmentService service;
    public AssignmentController(AssignmentService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@PathVariable UUID tenantId,
            @Valid @RequestBody CreateAssignmentRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(tenantId, principal(auth, tenantId), request));
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID tenantId,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) UUID sectionId, Authentication auth) {
        return service.list(tenantId, principal(auth, tenantId), institutionId, sectionId);
    }

    @PostMapping(value = "/{assignmentId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> submit(@PathVariable UUID tenantId,
            @PathVariable UUID assignmentId, @RequestParam UUID studentProfileId,
            @RequestParam UUID institutionId, @RequestPart("file") MultipartFile file, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(tenantId, principal(auth, tenantId),
                assignmentId, studentProfileId, institutionId, file));
    }

    @PostMapping("/submissions/{submissionId}/grade")
    public Map<String, Object> grade(@PathVariable UUID tenantId, @PathVariable UUID submissionId,
            @RequestBody GradeSubmissionRequest request, Authentication auth) {
        return service.grade(tenantId, principal(auth, tenantId), submissionId, request.marksAwarded(), request.feedback(), request.rubricMarks());
    }

    private static TenantPrincipal principal(Authentication auth, UUID tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) throw new UnauthorizedException("Tenant authentication is required.");
        if (!tenantId.equals(p.tenantId())) throw new ResourceNotFoundException("Assignment not found.");
        return p;
    }

    public record GradeSubmissionRequest(BigDecimal marksAwarded, String feedback, List<AssignmentService.RubricMark> rubricMarks) {}
}
