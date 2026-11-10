package com.edu360.api.selfservice;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileObject;
import com.edu360.api.files.FileObjectId;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.files.LocalFileStorage;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.platform.PlatformUser;
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.selfservice.dto.ProfileResponse;
import com.edu360.api.selfservice.dto.ProfileUpdateRequest;

import lombok.RequiredArgsConstructor;

/**
 * PROFILE_EDIT_OWN (Sign-in epic, Scope: Self): "see and edit own profile and contact details."
 *
 * WHO "own" is: always resolved from the already-authenticated TenantPrincipal/PlatformPrincipal
 * the caller hands in (ProfileController builds these the same way AuthController.me() does,
 * from the Authentication principal) - never from a path/query parameter, so there is no way to
 * ask for someone else's profile through this class.
 *
 * API SHAPE DEVIATION: the V4.0 spec's own self-service path is /v1/tenants/{tenantId}/me - this
 * app has no such route family yet and every other authenticated endpoint already lives under
 * this app's own /api prefix (see AuthController's class comment, and the same /api-prefix
 * deviation UserCreationController already took for USER_CREATE). GET/PATCH /api/me/profile
 * follows that existing convention instead of introducing a one-off /v1 path.
 *
 * PHOTO: tenant principals only. app.file_object.tenant_id is NOT NULL, so a platform principal
 * (platform.platform_user has no tenant_id at all - a wholly separate identity space, see
 * PlatformUser's class comment) has no row in this schema it could ever own a file under -
 * uploadTenantPhoto/getTenantPhoto are tenant-only by name, and ProfileController rejects a
 * platform principal's attempt with a clear 403 rather than silently doing nothing.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final String PHOTO_PURPOSE = "PROFILE_PHOTO";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DELETED = "DELETED";
    private static final String SCAN_CLEAN = "CLEAN";
    private static final long MAX_PHOTO_BYTES = 2 * 1024 * 1024;
    private static final List<String> ALLOWED_PHOTO_CONTENT_TYPES = List.of("image/jpeg", "image/png");

    private final UserAccountRepository userAccountRepository;
    private final PlatformUserRepository platformUserRepository;
    private final FileObjectRepository fileObjectRepository;
    private final LocalFileStorage fileStorage;
    private final AuditLogService auditLogService;

    /** A-08: PROFILE_EDIT_OWN is checked at the user's own active scope. Set by Spring. */
    private com.edu360.api.rbac.PermissionEvaluatorService permissionEvaluatorService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setPermissionEvaluatorService(com.edu360.api.rbac.PermissionEvaluatorService permissionEvaluatorService) {
        this.permissionEvaluatorService = permissionEvaluatorService;
    }

    public ProfileResponse getTenantProfile(TenantPrincipal principal) {
        UserAccount account = loadTenantAccount(principal);
        return ProfileResponse.fromTenant(principal, account, resolvePhotoUrl(principal.tenantId(), principal.userAccountId()));
    }

    /** Shared with AuthService (see its own resolvePhotoUrl) so /api/auth/me, login and this
     *  profile screen all agree on exactly which stored file counts as "the current photo". */
    public String resolvePhotoUrl(UUID tenantId, UUID userAccountId) {
        boolean hasPhoto = fileObjectRepository
                .findFirstByIdTenantIdAndUploadedByAndPurposeAndStatusAndScanStatus(
                        tenantId, userAccountId, PHOTO_PURPOSE, STATUS_ACTIVE, SCAN_CLEAN)
                .isPresent();
        return hasPhoto ? "/me/profile/photo" : null;
    }

    /**
     * Validates type/size, stores the bytes, ends any previous photo(s) this user had (status ->
     * DELETED, never a hard delete - same "ended, never removed" convention as
     * UserRoleAssignment), then inserts the new ACTIVE file_object row. scanStatus is set CLEAN
     * directly - see the class javadoc on why there is no real scan to wait for yet.
     */
    public ProfileResponse uploadTenantPhoto(TenantPrincipal principal, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No photo file was provided");
        }
        if (!ALLOWED_PHOTO_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Profile photo must be a JPEG or PNG image");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException("Profile photo must be 2 MB or smaller");
        }

        UUID tenantId = principal.tenantId();
        UUID userAccountId = principal.userAccountId();
        byte[] content = readBytes(file);
        String sha256 = sha256Hex(content);
        UUID newFileId = UUID.randomUUID();
        String storageKey = fileStorage.store(tenantId, newFileId, new ByteArrayInputStream(content));

        List<FileObject> previous = fileObjectRepository
                .findByIdTenantIdAndUploadedByAndPurpose(tenantId, userAccountId, PHOTO_PURPOSE);
        for (FileObject old : previous) {
            if (STATUS_ACTIVE.equals(old.getStatus())) {
                old.setStatus(STATUS_DELETED);
                old.setUpdatedBy(userAccountId);
                fileObjectRepository.save(old);
            }
        }

        FileObject fileObject = FileObject.builder()
                .id(new FileObjectId(tenantId, newFileId))
                .purpose(PHOTO_PURPOSE)
                .storageKey(storageKey)
                .originalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo")
                .contentType(file.getContentType())
                .sizeBytes(content.length)
                .sha256(sha256)
                .scanStatus(SCAN_CLEAN)
                .status(STATUS_ACTIVE)
                .uploadedBy(userAccountId)
                .createdBy(userAccountId)
                .updatedBy(userAccountId)
                .build();
        fileObjectRepository.save(fileObject);

        auditLogService.record(AuditEventType.PROFILE_UPDATED, null, principal.displayName(), null, null);

        UserAccount account = loadTenantAccount(principal);
        return ProfileResponse.fromTenant(principal, account, resolvePhotoUrl(tenantId, userAccountId));
    }

    /** Streams the current user's own active, scanned-clean photo bytes + content type, or
     *  throws ResourceNotFoundException if they have none. */
    public StoredPhoto getTenantPhoto(TenantPrincipal principal) {
        FileObject fileObject = fileObjectRepository
                .findFirstByIdTenantIdAndUploadedByAndPurposeAndStatusAndScanStatus(
                        principal.tenantId(), principal.userAccountId(), PHOTO_PURPOSE, STATUS_ACTIVE, SCAN_CLEAN)
                .orElseThrow(() -> new ResourceNotFoundException("No profile photo set"));
        return new StoredPhoto(fileStorage.read(fileObject.getStorageKey()), fileObject.getContentType());
    }

    public record StoredPhoto(byte[] content, String contentType) {
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Could not read the uploaded photo", ex);
        }
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public ProfileResponse getPlatformProfile(PlatformPrincipal principal) {
        PlatformUser user = loadPlatformUser(principal);
        return ProfileResponse.fromPlatform(principal, user);
    }

    /** Applies only the fields ProfileUpdateRequest actually declares (displayName, mobile) -
     *  see that class's comment for why role/email are not among them. A null field on the
     *  request means "leave this one unchanged", not "clear it". */
    /** Audit text for a refused parent mobile change: states what happened and nothing else. */
    static final String PARENT_MOBILE_CHANGE_REFUSED_AUDIT =
            "Parent mobile change refused: no SMS provider is connected, so no code was sent or checked and no alert was sent; mobile unchanged.";

    public ProfileResponse updateTenantProfile(TenantPrincipal principal, ProfileUpdateRequest request) {
        if (permissionEvaluatorService != null) {
            com.edu360.api.rbac.RbacPrincipal self =
                    new com.edu360.api.rbac.RbacPrincipal(principal.tenantId(), principal.userAccountId());
            permissionEvaluatorService.authorize(self, "PROFILE_EDIT_OWN",
                    permissionEvaluatorService.ownScope(self), com.edu360.api.rbac.RbacTarget.of(principal.userAccountId()));
        }
        UserAccount account = loadTenantAccount(principal);
        if (request.getDisplayName() != null) {
            account.setDisplayName(request.getDisplayName());
        }
        if (request.getMobile() != null && !request.getMobile().equals(account.getMobile())) {
            // Rule WF-30 (story 5, I-08): a parent's new mobile must be confirmed with a code and the
            // old number alerted. No SMS provider is connected yet, so the change is refused and the
            // audit says exactly that; the number stays unchanged.
            if (account.getUserType() == com.edu360.api.identity.UserType.GUARDIAN) {
                auditLogService.record(AuditEventType.DENIED_ATTEMPT, null, principal.displayName(), null,
                        PARENT_MOBILE_CHANGE_REFUSED_AUDIT);
                throw new com.edu360.api.exception.BusinessRuleViolationException("PHONE_VERIFICATION_UNAVAILABLE",
                        "Rule WF-30: A parent's mobile number can only be changed after a code sent to the new number is confirmed. "
                                + "Text messages are not connected yet, so the number was not changed. Please contact the school office.");
            }
            account.setMobile(request.getMobile());
        }
        account.setUpdatedBy(principal.userAccountId());
        UserAccount saved = userAccountRepository.save(account);
        // Same actor-identifier convention as AuthController.logout: tenant principal -> displayName.
        auditLogService.record(AuditEventType.PROFILE_UPDATED, null, principal.displayName(), null, null);
        return ProfileResponse.fromTenant(principal, saved, resolvePhotoUrl(principal.tenantId(), principal.userAccountId()));
    }

    public ProfileResponse updatePlatformProfile(PlatformPrincipal principal, ProfileUpdateRequest request) {
        PlatformUser user = loadPlatformUser(principal);
        if (request.getDisplayName() != null) {
            user.setFullName(request.getDisplayName());
        }
        // request.getMobile() is deliberately ignored here: platform.platform_user has no
        // mobile column at all (see PlatformUser's class comment on why it is a wholly separate
        // identity space from the tenant user model) - silently accepting it would promise a
        // field this account type can never actually have.
        user.setUpdatedBy(principal.platformUserId());
        PlatformUser saved = platformUserRepository.save(user);
        // Same actor-identifier convention as AuthController.logout: platform principal -> email.
        auditLogService.record(AuditEventType.PROFILE_UPDATED, null, principal.email(), null, null);
        return ProfileResponse.fromPlatform(principal, saved);
    }

    private UserAccount loadTenantAccount(TenantPrincipal principal) {
        return userAccountRepository.findById(new UserAccountId(principal.tenantId(), principal.userAccountId()))
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
    }

    private PlatformUser loadPlatformUser(PlatformPrincipal principal) {
        return platformUserRepository.findById(principal.platformUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
    }
}
