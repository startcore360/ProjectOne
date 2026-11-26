package com.edu360.api.academic.controller;
import java.math.BigDecimal;import java.time.LocalDate;import java.util.List;import java.util.Map;import java.util.UUID;
import org.springframework.http.HttpStatus;import org.springframework.http.ResponseEntity;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import com.edu360.api.academic.service.AttendanceService;import com.edu360.api.academic.service.AttendanceService.AttendanceItem;
import com.edu360.api.config.TenantPrincipal;import com.edu360.api.exception.ResourceNotFoundException;import com.edu360.api.exception.UnauthorizedException;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.RequiredArgsConstructor;
@RestController @RequestMapping({"/api/tenants/{tenantId}/attendance","/v1/tenants/{tenantId}/attendance"}) @RequiredArgsConstructor
public class AttendanceController{
 private final AttendanceService service;
 @PostMapping("/sessions") public ResponseEntity<Map<String,Object>> create(@PathVariable UUID tenantId,@Valid @RequestBody SessionRequest body,Authentication auth){
  Map<String,Object> result=service.createSession(tenantId,principal(auth,tenantId),body.institutionId(),body.sectionId(),body.subjectGroupId(),body.timetableSlotId(),body.subjectAllocationId(),body.attendanceDate(),body.sessionKind(),body.notHeld(),body.toAttendanceItems());
  return ResponseEntity.status(HttpStatus.CREATED).body(result);
 }
 @GetMapping("/sections/{sectionId}/roster") public List<Map<String,Object>> roster(@PathVariable UUID tenantId,@PathVariable UUID sectionId,@RequestParam UUID institutionId,Authentication auth){return service.roster(tenantId,principal(auth,tenantId),institutionId,sectionId);}
 @GetMapping("/subject-groups/{groupId}/roster") public List<Map<String,Object>> groupRoster(@PathVariable UUID tenantId,@PathVariable UUID groupId,@RequestParam UUID institutionId,Authentication auth){return service.rosterForGroup(tenantId,principal(auth,tenantId),institutionId,groupId);}
 @GetMapping("/register") public List<Map<String,Object>> register(@PathVariable UUID tenantId,@RequestParam UUID institutionId,@RequestParam UUID sectionId,@RequestParam LocalDate date,Authentication auth){return service.register(tenantId,principal(auth,tenantId),institutionId,sectionId,date);}
 @GetMapping("/enrollments/{enrollmentId}/percentage") public Map<String,Object> percentage(@PathVariable UUID tenantId,@PathVariable UUID enrollmentId,@RequestParam UUID institutionId,Authentication auth){return service.percentage(tenantId,principal(auth,tenantId),institutionId,enrollmentId);}
 @GetMapping("/enrollments/{enrollmentId}/eligibility") public Map<String,Object> eligibility(@PathVariable UUID tenantId,@PathVariable UUID enrollmentId,@RequestParam UUID institutionId,Authentication auth){return service.eligibility(tenantId,principal(auth,tenantId),institutionId,enrollmentId);}
 @GetMapping("/thresholds") public Map<String,Object> getThreshold(@PathVariable UUID tenantId,@RequestParam UUID institutionId,@RequestParam(required=false) UUID departmentId,Authentication auth){return service.threshold(tenantId,principal(auth,tenantId),institutionId,departmentId);}
 @PutMapping("/thresholds") public Map<String,Object> threshold(@PathVariable UUID tenantId,@Valid @RequestBody ThresholdRequest body,Authentication auth){return service.updateThreshold(tenantId,principal(auth,tenantId),body.institutionId(),body.departmentId(),body.minimumAttendancePercentage(),body.effectiveFrom());}
 public record SessionRequest(@NotNull UUID institutionId,UUID sectionId,UUID subjectGroupId,UUID timetableSlotId,UUID subjectAllocationId,@NotNull LocalDate attendanceDate,
  @NotBlank @Pattern(regexp="DAILY|PERIOD|SCHOOL_FIRST_HALF|SCHOOL_SECOND_HALF") String sessionKind,boolean notHeld,@NotEmpty List<@Valid EntryRequest> entries){
  List<AttendanceItem> toAttendanceItems(){return entries.stream().map(e->new AttendanceItem(e.enrollmentId(),e.status())).toList();}
 }
 public record EntryRequest(@NotNull UUID enrollmentId,@NotBlank @Pattern(regexp="PRESENT|ABSENT|ON_APPROVED_LEAVE|NOT_HELD") String status){}
 public record ThresholdRequest(@NotNull UUID institutionId,UUID departmentId,@NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal minimumAttendancePercentage,LocalDate effectiveFrom){}
 private static TenantPrincipal principal(Authentication auth,UUID tenant){if(auth==null||!(auth.getPrincipal() instanceof TenantPrincipal p))throw new UnauthorizedException("Tenant authentication is required.");if(!tenant.equals(p.tenantId()))throw new ResourceNotFoundException("Tenant not found.");return p;}
}