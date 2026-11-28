package com.edu360.api.platformops.landing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileScanner;
import com.edu360.api.platformops.landing.dto.LandingMediaUploadResponse;
import com.edu360.api.platformops.role.PlatformAuditWriter;

@Service
public class LandingMediaService {
    static final long MAX_IMAGE_BYTES = 5_242_880L; // 5 MiB
    static final long MAX_VIDEO_BYTES = 26_214_400L; // 25 MiB

    private final LandingMediaRepository mediaRepository;
    private final FileScanner fileScanner;
    private final PlatformAuditWriter auditWriter;
    private final boolean allowPendingDownload;

    public LandingMediaService(
            LandingMediaRepository mediaRepository,
            FileScanner fileScanner,
            PlatformAuditWriter auditWriter,
            @Value("${files.allow-pending-download:false}") boolean allowPendingDownload) {
        this.mediaRepository = mediaRepository;
        this.fileScanner = fileScanner;
        this.auditWriter = auditWriter;
        this.allowPendingDownload = allowPendingDownload;
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public LandingMediaUploadResponse upload(
            MultipartFile file, String mediaKindRaw, PlatformPrincipal actor) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty media file is required.");
        }
        String mediaKind = mediaKindRaw == null ? "" : mediaKindRaw.trim().toUpperCase();
        if (!"IMAGE".equals(mediaKind) && !"VIDEO".equals(mediaKind)) {
            throw new IllegalArgumentException("mediaKind must be IMAGE or VIDEO.");
        }

        long maxBytes = "IMAGE".equals(mediaKind) ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleViolationException(
                    "PAYLOAD_TOO_LARGE",
                    "IMAGE".equals(mediaKind) ? "Images may not exceed 5 MiB." : "Videos may not exceed 25 MiB.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Could not read uploaded media file.", ex);
        }
        if (content.length == 0 || content.length > maxBytes) {
            throw new BusinessRuleViolationException("PAYLOAD_TOO_LARGE", "Media exceeds permitted size limit.");
        }

        String contentType = detectSafeMediaType(mediaKind, content);
        FileScanner.ScanResult scanResult = fileScanner.scan(content);
        if (scanResult == FileScanner.ScanResult.INFECTED) {
            throw new BusinessRuleViolationException("MALWARE_DETECTED", "Upload rejected by security scanner.");
        }

        UUID mediaId = UUID.randomUUID();
        String safeName = sanitizeFilename(file.getOriginalFilename());
        String checksum = sha256(content);

        LandingMedia saved = mediaRepository.save(LandingMedia.builder()
                .mediaId(mediaId)
                .mediaKind(mediaKind)
                .fileName(safeName)
                .contentType(contentType)
                .byteSize(content.length)
                .sha256(checksum)
                .scanStatus(scanResult.name())
                .status("ACTIVE")
                .content(content)
                .createdBy(actor.platformUserId())
                .build());

        auditWriter.record(actor, "CREATE", "LANDING_CMS_MANAGE", "LANDING_MEDIA", mediaId);

        return new LandingMediaUploadResponse(
                saved.getMediaId(),
                saved.getMediaKind(),
                saved.getFileName(),
                saved.getContentType(),
                saved.getByteSize(),
                saved.getScanStatus(),
                "/api/public/landing/media/" + saved.getMediaId()
        );
    }

    @Transactional(readOnly = true, transactionManager = "platformTransactionManager")
    public LandingMedia loadPublicMedia(UUID mediaId) {
        LandingMedia media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found."));
        if (!"ACTIVE".equals(media.getStatus())
                || "INFECTED".equals(media.getScanStatus())
                || "SCAN_FAILED".equals(media.getScanStatus())
                || (!"CLEAN".equals(media.getScanStatus()) && !"PENDING".equals(media.getScanStatus()))) {
            throw new BusinessRuleViolationException("FILE_NOT_AVAILABLE", "This media asset is not available.");
        }
        if ("PENDING".equals(media.getScanStatus()) && !allowPendingDownload) {
            throw new BusinessRuleViolationException("FILE_NOT_AVAILABLE", "This media asset is awaiting security scanning.");
        }
        return media;
    }

    private static String detectSafeMediaType(String mediaKind, byte[] bytes) {
        if ("IMAGE".equals(mediaKind)) {
            if (isPng(bytes)) return "image/png";
            if (isJpeg(bytes)) return "image/jpeg";
            if (isWebp(bytes)) return "image/webp";
            throw new BusinessRuleViolationException("FILE_TYPE_NOT_ALLOWED", "Images must be PNG, JPEG, or WebP.");
        }
        if (isMp4(bytes)) return "video/mp4";
        if (isWebm(bytes)) return "video/webm";
        throw new BusinessRuleViolationException("FILE_TYPE_NOT_ALLOWED", "Videos must be MP4 or WebM.");
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static boolean isMp4(byte[] b) {
        return b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
    }

    private static boolean isWebm(byte[] b) {
        return b.length >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45
                && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3;
    }

    private static String sanitizeFilename(String original) {
        String base = (original == null || original.isBlank()) ? "asset" : original;
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFKC)
                .replaceAll("[\\\\/]+", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
