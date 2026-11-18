package com.edu360.api.platformops.release;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.release.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping({"/v1/platform/releases","/api/platform/releases"}) @RequiredArgsConstructor
public class PlatformReleaseController {
 private final PlatformReleaseService releaseService;
 private final PlatformPermissionService permissionService;
 @GetMapping public ResponseEntity<List<ReleaseResponse>> listReleases(Authentication auth) {
  PlatformPrincipal p=(PlatformPrincipal)auth.getPrincipal(); permissionService.authorize(p,"RELEASE_MANAGE");
  return ResponseEntity.ok(releaseService.listReleases());
 }
 @GetMapping("/{id}") public ResponseEntity<ReleaseResponse> getRelease(@PathVariable UUID id,Authentication auth) {
  PlatformPrincipal p=(PlatformPrincipal)auth.getPrincipal(); permissionService.authorize(p,"RELEASE_MANAGE");
  return ResponseEntity.ok(releaseService.getRelease(id));
 }
 @PostMapping public ResponseEntity<ReleaseResponse> createRelease(@Valid @RequestBody CreateReleaseRequest req,Authentication auth) {
  PlatformPrincipal p=(PlatformPrincipal)auth.getPrincipal(); permissionService.authorize(p,"RELEASE_MANAGE");
  return ResponseEntity.status(HttpStatus.CREATED).body(releaseService.createRelease(req,p));
 }
 @PostMapping("/{id}/schedule") public ResponseEntity<ReleaseResponse> scheduleRelease(@PathVariable UUID id,@Valid @RequestBody ScheduleReleaseRequest req,Authentication auth) {
  PlatformPrincipal p=(PlatformPrincipal)auth.getPrincipal(); permissionService.authorize(p,"RELEASE_MANAGE");
  return ResponseEntity.ok(releaseService.scheduleRelease(id,req.at(),p));
 }
 @PostMapping("/{id}/transitions") public ResponseEntity<ReleaseResponse> transitionRelease(@PathVariable UUID id,@Valid @RequestBody ReleaseTransitionRequest req,Authentication auth) {
  PlatformPrincipal p=(PlatformPrincipal)auth.getPrincipal(); permissionService.authorize(p,"RELEASE_MANAGE");
  return ResponseEntity.ok(releaseService.transitionRelease(id,req,p));
 }
 @PatchMapping("/{id}") public ResponseEntity<ReleaseResponse> patchRelease(@PathVariable UUID id,@Valid @RequestBody UpdateReleaseRequest req,Authentication auth) { return updateRelease(id,req,auth); }
 @PutMapping("/{id}") public ResponseEntity<ReleaseResponse> updateRelease(@PathVariable UUID id,@Valid @RequestBody UpdateReleaseRequest req,Authentication auth) {
  PlatformPrincipal p=(PlatformPrincipal)auth.getPrincipal(); permissionService.authorize(p,"RELEASE_MANAGE");
  return ResponseEntity.ok(releaseService.updateRelease(id,req,p));
 }
}
