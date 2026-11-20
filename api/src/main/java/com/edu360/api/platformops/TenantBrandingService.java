package com.edu360.api.platformops;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileStorage;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/**
 * WHAT: tenant branding (plan E1-S4, R2-14, D2 row 205) - a logo on every plan, and primary/accent
 * colours only on plans with branding_colors_allowed.
 *
 * HOW: the logo goes through FileStorage (local in dev, object storage later) and its key and type
 * are kept on platform.tenant (migration 52) - not app.file_object, which would need a tenant user
 * as the uploader. The file's real content decides its type, never its name: PNG by its signature,
 * SVG by its root element, and an SVG that can run script is refused. Every change is audited with
 * old and new values.
 */
@Service
public class TenantBrandingService {

    static final int MAX_LOGO_BYTES = 512 * 1024;
    static final String PNG = "image/png";
    static final String SVG = "image/svg+xml";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern SVG_ROOT = Pattern.compile("^(?:\\s|<\\?xml[^>]*>|<!--.*?-->|<!DOCTYPE svg[^>]*>)*<svg[\\s>]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** Anything in an SVG that could run script or pull in outside content. */
    private static final Pattern SVG_UNSAFE = Pattern.compile(
            "<script|javascript:|\\son[a-z]+\\s*=|<foreignobject|<iframe|<embed|<object|<!entity|(?:xlink:)?href\\s*=\\s*[\"']\\s*(?:https?:|data:text)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_KEY = Pattern.compile("\"(primary|accent)\"\\s*:\\s*\"(#[0-9A-Fa-f]{6})\"");

    public record Branding(boolean hasLogo, UUID logoVersion, String primary, String accent, boolean colorsAllowed) {
    }

    public record Logo(byte[] content, String contentType) {
    }

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final FileStorage fileStorage;
    private final PlatformAuditWriter platformAuditWriter;

    public TenantBrandingService(TenantRepository tenantRepository, PlanRepository planRepository,
                                 FileStorage fileStorage, PlatformAuditWriter platformAuditWriter) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.fileStorage = fileStorage;
        this.platformAuditWriter = platformAuditWriter;
    }

    public Branding get(UUID tenantId) {
        Tenant tenant = tenant(tenantId);
        Map<String, String> colors = parseColors(tenant.getBrandingColors());
        return new Branding(tenant.getBrandingLogoStorageKey() != null, tenant.getBrandingLogoFileRef(),
                colors.get("primary"), colors.get("accent"), colorsAllowed(tenant));
    }

    public Logo logo(UUID tenantId) {
        Tenant tenant = tenant(tenantId);
        if (tenant.getBrandingLogoStorageKey() == null) {
            throw new ResourceNotFoundException("This tenant has no logo.");
        }
        return new Logo(fileStorage.read(tenant.getBrandingLogoStorageKey()), tenant.getBrandingLogoContentType());
    }

    /**
     * Changes what is given and leaves the rest. Colours: a non-blank value sets it, a blank value
     * clears it, null leaves it. Setting a colour needs a plan that allows colours (422); clearing
     * one is always allowed. Logo: new bytes replace it; removeLogo clears it.
     */
    public Branding update(UUID tenantId, byte[] logoBytes, String primary, String accent, boolean removeLogo,
                           PlatformPrincipal principal) {
        Tenant tenant = tenant(tenantId);
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();

        Map<String, String> colors = new LinkedHashMap<>(parseColors(tenant.getBrandingColors()));
        boolean setsColor = applyColor(colors, "primary", primary) | applyColor(colors, "accent", accent);
        if (setsColor && !colorsAllowed(tenant)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BRANDING_COLORS_NOT_IN_PLAN",
                    "This tenant's plan does not include brand colours. The logo can still be changed.");
        }
        String newColors = colors.isEmpty() ? null : toJson(colors);
        if (!Objects.equals(newColors, tenant.getBrandingColors())) {
            change(changes, "colors", tenant.getBrandingColors(), newColors);
            tenant.setBrandingColors(newColors);
        }

        if (logoBytes != null && logoBytes.length > 0) {
            String contentType = checkLogo(logoBytes);
            UUID fileId = UUID.randomUUID();
            String key = fileStorage.store(tenantId, fileId, new ByteArrayInputStream(logoBytes));
            change(changes, "logo", tenant.getBrandingLogoFileRef(), fileId);
            tenant.setBrandingLogoFileRef(fileId);
            tenant.setBrandingLogoStorageKey(key);
            tenant.setBrandingLogoContentType(contentType);
        } else if (removeLogo && tenant.getBrandingLogoStorageKey() != null) {
            change(changes, "logo", tenant.getBrandingLogoFileRef(), null);
            tenant.setBrandingLogoFileRef(null);
            tenant.setBrandingLogoStorageKey(null);
            tenant.setBrandingLogoContentType(null);
        }

        if (!changes.isEmpty()) {
            tenant.setUpdatedAt(Instant.now());
            tenant.setUpdatedBy(principal.platformUserId());
            tenantRepository.save(tenant);
            platformAuditWriter.record(principal, tenantId, "TENANT_BRANDING_UPDATED", "UPDATE", "TENANT_SETTINGS",
                    "TENANT", tenantId, PlatformAuditWriter.SUCCESS, changes);
        }
        return get(tenantId);
    }

    /** Returns the real content type, or refuses the file (422). */
    static String checkLogo(byte[] bytes) {
        if (bytes.length > MAX_LOGO_BYTES) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LOGO_TOO_LARGE",
                    "The logo must be 512 KB or smaller.");
        }
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return PNG;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        if (SVG_ROOT.matcher(text).find()) {
            if (SVG_UNSAFE.matcher(text).find()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LOGO_UNSAFE",
                        "This SVG contains scripts or outside links. Save it again as a plain image, or use a PNG.");
            }
            return SVG;
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LOGO_TYPE",
                "The logo must be a PNG or SVG file.");
    }

    private Tenant tenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " was not found."));
    }

    private boolean colorsAllowed(Tenant tenant) {
        if (tenant.getPlanId() == null) {
            return false;
        }
        return planRepository.findById(tenant.getPlanId()).map(Plan::isBrandingColorsAllowed).orElse(false);
    }

    /** True when the call sets (not clears) a colour. */
    private static boolean applyColor(Map<String, String> colors, String key, String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            colors.remove(key);
            return false;
        }
        if (!HEX.matcher(trimmed).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COLOR",
                    "Colours must look like #1A2B3C.");
        }
        String normalised = trimmed.toUpperCase(Locale.ROOT);
        boolean changed = !normalised.equals(colors.get(key));
        colors.put(key, normalised);
        return changed;
    }

    static Map<String, String> parseColors(String json) {
        Map<String, String> colors = new LinkedHashMap<>();
        if (json != null) {
            Matcher m = COLOR_KEY.matcher(json);
            while (m.find()) {
                colors.put(m.group(1), m.group(2).toUpperCase(Locale.ROOT));
            }
        }
        return colors;
    }

    /** Values are already checked against #RRGGBB, so plain string building is safe here. */
    private static String toJson(Map<String, String> colors) {
        StringBuilder sb = new StringBuilder("{");
        colors.forEach((k, v) -> sb.append(sb.length() > 1 ? "," : "").append('"').append(k).append("\":\"").append(v).append('"'));
        return sb.append('}').toString();
    }

    private static void change(Map<String, Map<String, Object>> changes, String field, Object oldValue, Object newValue) {
        Map<String, Object> c = new java.util.HashMap<>();
        c.put("old", oldValue);
        c.put("new", newValue);
        changes.put(field, c);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
