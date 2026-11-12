package com.edu360.api.files;

import java.io.ByteArrayInputStream;
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

import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;

@Service
public class FileService {
    static final long MAX_BYTES = 10_485_760L;
    private final FileObjectRepository repository;
    private final LocalFileStorage storage;
    private final PermissionEvaluatorService permissions;
    private final FileContentInspector inspector;
    private final FileScanner scanner;
    private final FileAccessPolicy accessPolicy;
    private final AuditLogService auditLog;
    private final boolean allowPendingDownload;

    public FileService(FileObjectRepository repository, LocalFileStorage storage,
            PermissionEvaluatorService permissions, FileContentInspector inspector,
            FileScanner scanner, FileAccessPolicy accessPolicy, AuditLogService auditLog,
            @Value("${files.allow-pending-download:false}") boolean allowPendingDownload) {
        this.repository = repository;
        this.storage = storage;
        this.permissions = permissions;
        this.inspector = inspector;
        this.scanner = scanner;
        this.accessPolicy = accessPolicy;
        this.auditLog = auditLog;
        this.allowPendingDownload = allowPendingDownload;
    }

    @Transactional
    public FileUploadResponse upload(TenantPrincipal principal, MultipartFile upload, String purposeValue, UUID requestedInstitutionId) {
        if (upload == null || upload.isEmpty()) throw new IllegalArgumentException("A non-empty file is required.");
        if (upload.getSize() > MAX_BYTES) throw new BusinessRuleViolationException("PAYLOAD_TOO_LARGE", "Files may not exceed 10 MiB.");
        FilePurpose purpose = FilePurpose.parse(purposeValue);
        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        UserRoleAssignment active = permissions.activeAssignment(caller)
                .orElseThrow(() -> new UnauthorizedException("An active role is required to upload files."));
        UUID institutionId = requestedInstitutionId != null ? requestedInstitutionId : active.getInstitutionId();
        if (requestedInstitutionId != null && active.getInstitutionId() != null
                && !requestedInstitutionId.equals(active.getInstitutionId()) && active.getScopeType() != ScopeType.TENANT) {
            throw new ResourceNotFoundException("Institution not found in the active role scope.");
        }
        RbacScope scope = requestedInstitutionId == null
                ? PermissionEvaluatorService.scopeOf(active)
                : permissions.targetScope(caller, institutionId, active.getDepartmentId());
        permissions.authorize(caller, purpose.actionCode(), scope, RbacTarget.NONE);

        byte[] content;
        try { content = upload.getBytes(); }
        catch (java.io.IOException ex) { throw new IllegalArgumentException("Could not read uploaded file.", ex); }
        if (content.length > MAX_BYTES) throw new BusinessRuleViolationException("PAYLOAD_TOO_LARGE", "Files may not exceed 10 MiB.");

        String filename = sanitizeFilename(upload.getOriginalFilename());
        String contentType;
        try { contentType = inspector.inspect(filename, upload.getContentType(), content); }
        catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("FILE_TYPE_NOT_ALLOWED", "File content does not match the permitted safe file types.");
        }

        FileScanner.ScanResult scan = scanner.scan(content);
        UUID fileId = UUID.randomUUID();
        String checksum = sha256(content);
        String storageKey = storage.store(principal.tenantId(), fileId, new ByteArrayInputStream(content));
        FileObject file = FileObject.builder().id(new FileObjectId(principal.tenantId(), fileId))
                .institutionId(institutionId).purpose(purpose.name()).storageKey(storageKey).originalFilename(filename)
                .contentType(contentType).sizeBytes(content.length).sha256(checksum)
                .scanStatus(scan.name()).status(scan == FileScanner.ScanResult.INFECTED ? "QUARANTINED" : "ACTIVE")
                .uploadedBy(principal.userAccountId()).createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId()).build();
        FileObject saved = repository.save(file);

        // Audit persisted metadata only. Never write the original filename or file bytes to audit.
        auditLog.record(new AuditEntry(AuditEventType.FILE_UPLOADED, purpose.actionCode(), "FILE_OBJECT",
                fileId, institutionId, null,
                Map.of("purpose", purpose.name(), "size", content.length,
                        "sha256", checksum, "scan_status", scan.name(), "file_status", scan == FileScanner.ScanResult.INFECTED ? "QUARANTINED" : "ACTIVE"),
                1, "P", "SUCCESS"));
        return FileUploadResponse.from(saved);
    }

    @Transactional
    public StoredFile download(TenantPrincipal principal, UUID fileId) {
        FileObject file = repository.findById(new FileObjectId(principal.tenantId(), fileId))
                .orElseThrow(() -> new ResourceNotFoundException("File not found."));
        if ("DELETED".equals(file.getStatus()) || "QUARANTINED".equals(file.getStatus())
                || "INFECTED".equals(file.getScanStatus()) || "SCAN_FAILED".equals(file.getScanStatus())
                || (!"CLEAN".equals(file.getScanStatus()) && !"PENDING".equals(file.getScanStatus()))) {
            throw new BusinessRuleViolationException("FILE_NOT_AVAILABLE", "This file is not available for download.");
        }
        if ("PENDING".equals(file.getScanStatus()) && !allowPendingDownload) {
            throw new BusinessRuleViolationException("FILE_NOT_AVAILABLE", "This file is awaiting security scanning.");
        }
        if (!accessPolicy.canAccess(file, principal)) {
            throw new UnauthorizedException("The owning record has not granted file access.");
        }

        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        RbacScope scope = file.getInstitutionId() == null
                ? permissions.ownScope(caller)
                : permissions.targetScope(caller, file.getInstitutionId(), null);
        switch (FilePurpose.valueOf(file.getPurpose())) {
            case STUDY_MATERIAL -> permissions.authorize(caller, "STUDY_MATERIAL_VIEW", scope, RbacTarget.NONE);
            case ASSIGNMENT_SUBMISSION -> {
                if (!permissions.can(caller, "ASSIGNMENT_VIEW_OWN", scope, RbacTarget.NONE).allowed()
                        && !permissions.can(caller, "ASSIGNMENT_EVALUATE", scope, RbacTarget.NONE).allowed()) {
                    throw new UnauthorizedException("The active role cannot view this assignment submission.");
                }
            }
            case SYLLABUS -> permissions.authorize(caller, "SYLLABUS_VIEW", scope, RbacTarget.NONE);
            case STAFF_DOCUMENT -> {
                if (!permissions.can(caller, "STAFF_RECORD_VIEW", scope, RbacTarget.NONE).allowed()
                        && !permissions.can(caller, "STAFF_RECORD_MANAGE", scope, RbacTarget.NONE).allowed()) {
                    throw new UnauthorizedException("The active role cannot view staff documents.");
                }
            }
            case ATTENDANCE_UPLOAD -> {
                if (!permissions.can(caller, "ATTENDANCE_VIEW", scope, RbacTarget.NONE).allowed()
                        && !permissions.can(caller, "ATTENDANCE_BULK_UPLOAD", scope, RbacTarget.NONE).allowed()) {
                    throw new UnauthorizedException("The active role cannot view attendance imports.");
                }
            }
        }
        if (FilePurpose.STAFF_DOCUMENT.name().equals(file.getPurpose())) {
            auditLog.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW, "STAFF_RECORD_VIEW",
                    "FILE_OBJECT", fileId, file.getInstitutionId(), null,
                    Map.of("purpose", FilePurpose.STAFF_DOCUMENT.name()), 1, "P", "SUCCESS"));
        }
        return new StoredFile(storage.openRead(file.getStorageKey()), file.getOriginalFilename(), file.getContentType());
    }

    private static String sanitizeFilename(String supplied) {
        String value = supplied == null ? "upload" : supplied.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1);
        value = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}]", "").replaceAll("[. ]+$", "").trim();
        if (value.isBlank()) throw new IllegalArgumentException("A valid filename is required.");
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable.", ex); }
    }
}
