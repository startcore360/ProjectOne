package com.edu360.api.platformops.release;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.release.dto.*;
@Service
public class PlatformReleaseService {
 private final PlatformReleaseRepository releaseRepository;
 private final com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter;
 @org.springframework.beans.factory.annotation.Autowired
 public PlatformReleaseService(PlatformReleaseRepository r,com.edu360.api.platformops.role.PlatformAuditWriter a){releaseRepository=r;platformAuditWriter=a;}
 private static final Set<String> VALID_STATUSES=Set.of("UPLOADED","STAGING_TESTED","DEPLOYED","ROLLED_BACK");
 @Transactional public ReleaseResponse createRelease(CreateReleaseRequest req,PlatformPrincipal p) {
  if(releaseRepository.findByVersionLabel(req.versionLabel()).isPresent()) throw new DuplicateResourceException("Release with version label already exists: "+req.versionLabel());
  Instant now=Instant.now();
  PlatformRelease r=PlatformRelease.builder().releaseId(UUID.randomUUID()).versionLabel(req.versionLabel()).releaseNotes(req.releaseNotes())
   .hotfix(req.isHotfix()).status("UPLOADED").schemaVersion(req.schemaVersion()).createdAt(now).createdBy(p.platformUserId())
   .updatedAt(now).updatedBy(p.platformUserId()).rowVersion(1).build();
  PlatformRelease saved=releaseRepository.save(r); platformAuditWriter.record(p,"RELEASE_CREATED","RELEASE_MANAGE","PLATFORM_RELEASE",saved.getReleaseId());
  return ReleaseResponse.fromEntity(saved);
 }
 @Transactional public ReleaseResponse scheduleRelease(UUID id,Instant at,PlatformPrincipal p) {
  PlatformRelease r=releaseRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Release not found: "+id));
  if(!"STAGING_TESTED".equals(r.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"NOT_STAGING_TESTED");
  if(at==null||!at.isAfter(Instant.now())) throw new BusinessRuleViolationException("SCHEDULE_TIME_MUST_BE_FUTURE","Scheduled deployment time must be in the future.");
  Instant now=Instant.now(); r.setScheduledAt(at); r.setUpdatedAt(now); r.setUpdatedBy(p.platformUserId());
  PlatformRelease saved=releaseRepository.save(r);
  platformAuditWriter.record(p,null,"RELEASE_SCHEDULED","CONFIG_CHANGE","RELEASE_MANAGE","PLATFORM_RELEASE",saved.getReleaseId(),"SUCCESS",
   Map.of("scheduled_at",Map.of("old","", "new",at.toString())));
  return ReleaseResponse.fromEntity(saved);
 }
 @Transactional public ReleaseResponse transitionRelease(UUID id,ReleaseTransitionRequest req,PlatformPrincipal p) {
  PlatformRelease r=releaseRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Release not found: "+id));
  String target=req.targetStatus(); if(!VALID_STATUSES.contains(target)) throw new BusinessRuleViolationException("Invalid target release status: "+target);
  Instant now=Instant.now();
  if("DEPLOYED".equals(target)) {
   if(!"STAGING_TESTED".equals(r.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"NOT_STAGING_TESTED");
   boolean due=r.getScheduledAt()!=null&&!r.getScheduledAt().isAfter(now);
   boolean override=p.platformRole()==PlatformRole.PRODUCT_OWNER&&req.reason()!=null&&!req.reason().isBlank();
   if(!due&&!override) throw new ResponseStatusException(HttpStatus.CONFLICT,"DEPLOYMENT_REQUIRES_DUE_SCHEDULE_OR_PRODUCT_OWNER_REASON");
   if(!due) platformAuditWriter.record(p,null,"RELEASE_DEPLOY_OVERRIDE","CONFIG_CHANGE","RELEASE_MANAGE","PLATFORM_RELEASE",r.getReleaseId(),"SUCCESS",
     Map.of("reason",Map.of("old","","new",req.reason().trim())));
   r.setDeployedAt(now);
  } else if("ROLLED_BACK".equals(target)) r.setRolledBackAt(now);
  r.setStatus(target); r.setUpdatedAt(now); r.setUpdatedBy(p.platformUserId());
  PlatformRelease saved=releaseRepository.save(r); platformAuditWriter.record(p,"RELEASE_TRANSITIONED_"+target,"RELEASE_MANAGE","PLATFORM_RELEASE",saved.getReleaseId());
  return ReleaseResponse.fromEntity(saved);
 }
 @Transactional public ReleaseResponse updateRelease(UUID id,UpdateReleaseRequest req,PlatformPrincipal p) {
  PlatformRelease r=releaseRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Release not found: "+id));
  r.setReleaseNotes(req.releaseNotes()); r.setHotfix(req.isHotfix()); r.setSchemaVersion(req.schemaVersion()); r.setUpdatedAt(Instant.now()); r.setUpdatedBy(p.platformUserId());
  PlatformRelease saved=releaseRepository.save(r); platformAuditWriter.record(p,"RELEASE_UPDATED","RELEASE_MANAGE","PLATFORM_RELEASE",saved.getReleaseId()); return ReleaseResponse.fromEntity(saved);
 }
 @Transactional(readOnly=true) public List<ReleaseResponse> listReleases(){return releaseRepository.findAllByOrderByCreatedAtDesc().stream().map(ReleaseResponse::fromEntity).toList();}
 @Transactional(readOnly=true) public ReleaseResponse getRelease(UUID id){return releaseRepository.findById(id).map(ReleaseResponse::fromEntity).orElseThrow(()->new ResourceNotFoundException("Release not found: "+id));}
}
