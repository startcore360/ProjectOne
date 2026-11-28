package com.edu360.api.academic.controller;
import java.time.Instant;import java.time.LocalDate;import java.util.List;import java.util.Map;import java.util.UUID;
import org.springframework.http.HttpStatus;import org.springframework.http.ResponseEntity;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import com.edu360.api.academic.service.TimetableService;import com.edu360.api.config.TenantPrincipal;import com.edu360.api.exception.ResourceNotFoundException;import com.edu360.api.exception.UnauthorizedException;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.RequiredArgsConstructor;
@RestController @RequestMapping({"/api/tenants/{tenantId}/timetable","/v1/tenants/{tenantId}/timetable","/api/tenants/{tenantId}/academic/timetable","/v1/tenants/{tenantId}/academic/timetable"}) @RequiredArgsConstructor
public class TimetableController{
 private final TimetableService service;
 @GetMapping public List<Map<String,Object>> list(@PathVariable UUID tenantId,@RequestParam UUID institutionId,@RequestParam(required=false) UUID sectionId,@RequestParam(required=false) UUID subjectGroupId,@RequestParam(required=false) UUID academicSessionId,Authentication auth){return service.list(tenantId,principal(auth,tenantId),institutionId,sectionId,subjectGroupId,academicSessionId);}
 @GetMapping("/slots") public List<Map<String,Object>> filteredSlots(@PathVariable UUID tenantId,@RequestParam UUID institutionId,
  @RequestParam(required=false) UUID sectionId,@RequestParam(required=false) UUID subjectGroupId,@RequestParam(required=false) UUID academicSessionId,
  @RequestParam(required=false) UUID teacherId,@RequestParam(required=false) UUID roomId,@RequestParam(required=false) LocalDate date,
  @RequestParam(required=false) @Min(1) @Max(7) Integer dayOfWeek,Authentication auth){
  return service.listFiltered(tenantId,principal(auth,tenantId),institutionId,sectionId,subjectGroupId,academicSessionId,teacherId,roomId,date,dayOfWeek);
 }
 @DeleteMapping("/slots/{slotId}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void deleteSlot(@PathVariable UUID tenantId,@PathVariable UUID slotId,@RequestParam UUID institutionId,Authentication auth){
  service.deleteSlot(tenantId,principal(auth,tenantId),institutionId,slotId);
 }
 @GetMapping("/rooms") public List<Map<String,Object>> rooms(@PathVariable UUID tenantId,@RequestParam UUID institutionId,Authentication auth){return service.listRooms(tenantId,principal(auth,tenantId),institutionId);}
 @PostMapping("/rooms") public ResponseEntity<Map<String,Object>> createRoom(@PathVariable UUID tenantId,@Valid @RequestBody RoomRequest body,Authentication auth){return ResponseEntity.status(HttpStatus.CREATED).body(service.createRoom(tenantId,principal(auth,tenantId),body.institutionId(),body.roomNumber(),body.building(),body.capacity(),body.roomType()));}
 @PostMapping("/slots/{slotId}/publish") public Map<String,Object> publishSlot(@PathVariable UUID tenantId,@PathVariable UUID slotId,@RequestParam UUID institutionId,Authentication auth){return service.publishSlot(tenantId,principal(auth,tenantId),institutionId,slotId);}
 @PutMapping("/slots/{slotId}") public Map<String,Object> updateSlot(@PathVariable UUID tenantId,@PathVariable UUID slotId,@Valid @RequestBody MoveSlotRequest body,Authentication auth){return service.updateSlot(tenantId,principal(auth,tenantId),body.institutionId(),slotId,body.dayOfWeek(),body.periodNo(),body.startTime(),body.endTime(),body.roomId());}
 @PostMapping("/slots") public ResponseEntity<Map<String,Object>> createSlot(@PathVariable UUID tenantId,@Valid @RequestBody SlotRequest body,Authentication auth){Map<String,Object> created=service.createSlot(tenantId,principal(auth,tenantId),body.institutionId(),body.sectionId(),body.subjectGroupId(),body.subjectAllocationId(),body.periodNo(),body.dayOfWeek(),body.startTime(),body.endTime(),body.roomId(),body.isOnline(),body.validFrom(),body.validTo());return ResponseEntity.status(HttpStatus.CREATED).body(created);}
 @PostMapping("/slots/{slotId}/meeting-link") public ResponseEntity<Map<String,Object>> createMeetingLink(@PathVariable UUID tenantId,@PathVariable UUID slotId,@Valid @RequestBody MeetingLinkRequest body,Authentication auth){return ResponseEntity.status(HttpStatus.CREATED).body(service.createMeetingLink(tenantId,principal(auth,tenantId),body.institutionId(),slotId,body.occurrenceDate(),body.provider(),body.joinUrl(),body.externalMeetingId(),body.tokenExpiresAt()));}
 @PostMapping("/meeting-link-access") public Map<String,Object> accessMeetingLink(@PathVariable UUID tenantId,@Valid @RequestBody AccessRequest body,Authentication auth){return service.accessOnlineMeeting(tenantId,principal(auth,tenantId),body.accessToken());}
 public record RoomRequest(@NotNull UUID institutionId,@NotBlank String roomNumber,String building,@Min(1) @Max(10000) int capacity,@NotBlank @Pattern(regexp="LECTURE_HALL|LAB|AUDITORIUM") String roomType){}
 public record MoveSlotRequest(@NotNull UUID institutionId,@Min(1) @Max(20) int periodNo,@Min(1) @Max(7) int dayOfWeek,@NotBlank String startTime,@NotBlank String endTime,UUID roomId){}
 public record SlotRequest(@NotNull UUID institutionId,UUID sectionId,UUID subjectGroupId,@NotNull UUID subjectAllocationId,@Min(1) @Max(20) int periodNo,@Min(1) @Max(7) int dayOfWeek,@NotBlank String startTime,@NotBlank String endTime,UUID roomId,boolean isOnline,@NotNull LocalDate validFrom,LocalDate validTo){}
 public record MeetingLinkRequest(@NotNull UUID institutionId,@NotNull LocalDate occurrenceDate,@NotBlank @Pattern(regexp="ZOOM|GOOGLE_MEET|MS_TEAMS") String provider,@NotBlank @Pattern(regexp="https://.+") String joinUrl,@Size(max=200) String externalMeetingId,@NotNull Instant tokenExpiresAt){}
 public record AccessRequest(@NotBlank String accessToken){}
 private static TenantPrincipal principal(Authentication auth,UUID tenant){if(auth==null||!(auth.getPrincipal() instanceof TenantPrincipal p))throw new UnauthorizedException("Tenant authentication is required.");if(!tenant.equals(p.tenantId()))throw new ResourceNotFoundException("Tenant not found.");return p;}
}