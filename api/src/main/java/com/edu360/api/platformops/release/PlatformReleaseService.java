package com.edu360.api.platformops.release;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platformops.release.dto.CreateReleaseRequest;
import com.edu360.api.platformops.release.dto.ReleaseResponse;
import com.edu360.api.platformops.release.dto.ReleaseTransitionRequest;

import lombok.RequiredArgsConstructor;

@Service
public class PlatformReleaseService {

    private final PlatformReleaseRepository releaseRepository;
    private final com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter;

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformReleaseService(
            PlatformReleaseRepository releaseRepository,
            com.edu360.api.platformops.role.PlatformAuditWriter platformAuditWriter
    ) {
        this.releaseRepository = releaseRepository;
        this.platformAuditWriter = platformAuditWriter;
    }

    private static final Set<String> VALID_STATUSES = Set.of("UPLOADED", "STAGING_TESTED", "DEPLOYED", "ROLLED_BACK");

    @Transactional
    public ReleaseResponse createRelease(CreateReleaseRequest req, PlatformPrincipal principal) {
        if (releaseRepository.findByVersionLabel(req.versionLabel()).isPresent()) {
            throw new DuplicateResourceException("Release with version label already exists: " + req.versionLabel());
        }

        Instant now = Instant.now();
        PlatformRelease release = PlatformRelease.builder()
                .releaseId(UUID.randomUUID())
                .versionLabel(req.versionLabel())
                .releaseNotes(req.releaseNotes())
                .hotfix(req.isHotfix())
                .status("UPLOADED")
                .schemaVersion(req.schemaVersion())
                .createdAt(now)
                .createdBy(principal.platformUserId())
                .updatedAt(now)
                .updatedBy(principal.platformUserId())
                .rowVersion(1)
                .build();

        PlatformRelease saved = releaseRepository.save(release);
        platformAuditWriter.record(principal, "RELEASE_CREATED", "RELEASE_MANAGE", "PLATFORM_RELEASE", saved.getReleaseId());
        return ReleaseResponse.fromEntity(saved);
    }

    @Transactional
    public ReleaseResponse transitionRelease(UUID releaseId, ReleaseTransitionRequest req, PlatformPrincipal principal) {
        PlatformRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + releaseId));

        String target = req.targetStatus();
        if (!VALID_STATUSES.contains(target)) {
            throw new BusinessRuleViolationException("Invalid target release status: " + target);
        }

        Instant now = Instant.now();
        if ("DEPLOYED".equals(target)) {
            release.setDeployedAt(now);
        } else if ("ROLLED_BACK".equals(target)) {
            release.setRolledBackAt(now);
        }

        release.setStatus(target);
        release.setUpdatedAt(now);
        release.setUpdatedBy(principal.platformUserId());

        PlatformRelease saved = releaseRepository.save(release);
        platformAuditWriter.record(principal, "RELEASE_TRANSITIONED_" + target, "RELEASE_MANAGE", "PLATFORM_RELEASE", saved.getReleaseId());
        return ReleaseResponse.fromEntity(saved);
    }

    @Transactional
    public ReleaseResponse updateRelease(UUID releaseId, com.edu360.api.platformops.release.dto.UpdateReleaseRequest req, PlatformPrincipal principal) {
        PlatformRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + releaseId));

        release.setReleaseNotes(req.releaseNotes());
        release.setHotfix(req.isHotfix());
        release.setSchemaVersion(req.schemaVersion());
        release.setUpdatedAt(Instant.now());
        release.setUpdatedBy(principal.platformUserId());

        PlatformRelease saved = releaseRepository.save(release);
        platformAuditWriter.record(principal, "RELEASE_UPDATED", "RELEASE_MANAGE", "PLATFORM_RELEASE", saved.getReleaseId());
        return ReleaseResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ReleaseResponse> listReleases() {
        return releaseRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ReleaseResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReleaseResponse getRelease(UUID releaseId) {
        return releaseRepository.findById(releaseId)
                .map(ReleaseResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + releaseId));
    }
}
