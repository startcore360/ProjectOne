package com.edu360.api.academic.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.edu360.api.academic.service.TimetableService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/timetable","/v1/tenants/{tenantId}/timetable"})
@RequiredArgsConstructor
public class TimetableController {
    private final TimetableService service;

    @GetMapping
    public List<Map<String,Object>> list(@PathVariable UUID tenantId,@RequestParam UUID institutionId,
            @RequestParam(required=false) UUID sectionId,@RequestParam(required=false) UUID academicSessionId,
            Authentication authentication) {
        return service.list(tenantId,principal(authentication,tenantId),institutionId,sectionId,academicSessionId);
    }

    @GetMapping("/rooms")
    public List<Map<String,Object>> rooms(@PathVariable UUID tenantId,@RequestParam UUID institutionId,Authentication authentication) {
        return service.listRooms(tenantId,principal(authentication,tenantId),institutionId);
    }

    @PostMapping("/rooms")
    public ResponseEntity<Map<String,Object>> createRoom(@PathVariable UUID tenantId,@Valid @RequestBody RoomRequest body,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRoom(tenantId,principal(authentication,tenantId),
                body.institutionId(),body.roomNumber(),body.building(),body.capacity(),body.roomType()));
    }

    @PutMapping("/slots/{slotId}")
    public Map<String,Object> updateSlot(@PathVariable UUID tenantId,@PathVariable UUID slotId,
            @Valid @RequestBody MoveSlotRequest body,Authentication authentication) {
        return service.updateSlot(tenantId,principal(authentication,tenantId),body.institutionId(),slotId,
                body.dayOfWeek(),body.startTime(),body.endTime(),body.roomId());
    }

    @PostMapping("/slots")
    public ResponseEntity<Map<String,Object>> createSlot(@PathVariable UUID tenantId,@Valid @RequestBody SlotRequest body,
            Authentication authentication) {
        Map<String,Object> created=service.createSlot(tenantId,principal(authentication,tenantId),body.institutionId(),
                body.academicSessionId(),body.sectionId(),body.courseOfferingId(),body.teacherId(),body.roomId(),
                body.dayOfWeek(),body.startTime(),body.endTime(),body.onlineMeetingUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/slots/{slotId}/online-session")
    public ResponseEntity<Map<String,Object>> createOnlineSession(@PathVariable UUID tenantId,@PathVariable UUID slotId,
            @Valid @RequestBody OnlineSessionRequest body,Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOnlineSession(tenantId,
                principal(authentication,tenantId),body.institutionId(),slotId,body.provider(),body.meetingUrl(),body.tokenExpiresAt()));
    }

    @PostMapping("/online-session-access")
    public Map<String,Object> accessOnlineSession(@PathVariable UUID tenantId,@RequestBody AccessRequest body,
            Authentication authentication) {
        return service.accessOnlineSession(tenantId,principal(authentication,tenantId),body.accessToken());
    }

    public record RoomRequest(@NotNull UUID institutionId,@NotBlank String roomNumber,String building,
            @Min(1) @Max(10000) int capacity,@NotBlank @Pattern(regexp="LECTURE_HALL|LAB|AUDITORIUM") String roomType) {}
    public record MoveSlotRequest(@NotNull UUID institutionId,@Min(1) @Max(7) int dayOfWeek,
            @NotBlank String startTime,@NotBlank String endTime,UUID roomId) {}
    public record SlotRequest(@NotNull UUID institutionId,@NotNull UUID academicSessionId,@NotNull UUID sectionId,
            @NotNull UUID courseOfferingId,@NotNull UUID teacherId,UUID roomId,@Min(1) @Max(7) int dayOfWeek,
            @NotBlank String startTime,@NotBlank String endTime,String onlineMeetingUrl) {}
    public record OnlineSessionRequest(@NotNull UUID institutionId,@NotBlank String provider,
            @NotBlank @Pattern(regexp="^https://.+") String meetingUrl,@NotNull Instant tokenExpiresAt) {}
    public record AccessRequest(@NotBlank String accessToken) {}

    private static TenantPrincipal principal(Authentication auth,UUID tenantId){
        if(auth==null || !(auth.getPrincipal() instanceof TenantPrincipal p)) throw new UnauthorizedException("Tenant authentication is required.");
        if(!tenantId.equals(p.tenantId())) throw new ResourceNotFoundException("Tenant not found.");
        return p;
    }
}