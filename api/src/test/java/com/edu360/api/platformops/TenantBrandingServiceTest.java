package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ApiException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.files.FileStorage;
import com.edu360.api.identity.UserType;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

/** E1-S4: logo on every plan (PNG/SVG, 512 KB, no scripts), colours only when the plan allows them. */
@ExtendWith(MockitoExtension.class)
class TenantBrandingServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    private static final byte[] SVG = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"><circle r=\"4\"/></svg>"
            .getBytes(StandardCharsets.UTF_8);

    @Mock private TenantRepository tenants;
    @Mock private PlanRepository plans;
    @Mock private FileStorage storage;
    @Mock private PlatformAuditWriter audit;

    private TenantBrandingService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private Tenant tenant;
    private final PlatformPrincipal owner = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PRODUCT_OWNER, "po@x.test", false);

    @BeforeEach
    void setUp() {
        service = new TenantBrandingService(tenants, plans, storage, audit);
        tenant = Tenant.builder().tenantId(tenantId).planId(planId).build();
        org.mockito.Mockito.lenient().when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    }

    private void planAllowsColors(boolean allowed) {
        when(plans.findById(planId)).thenReturn(Optional.of(Plan.builder().planId(planId).brandingColorsAllowed(allowed).build()));
    }

    @Test
    void aPngLogoIsStoredOnEveryPlan_andTheChangeIsAudited() {
        planAllowsColors(false);
        when(storage.store(eq(tenantId), any(), any())).thenReturn("tenants/x/logo");

        TenantBrandingService.Branding result = service.update(tenantId, PNG, null, null, false, owner);

        assertThat(result.hasLogo()).isTrue();
        assertThat(tenant.getBrandingLogoContentType()).isEqualTo("image/png");
        assertThat(tenant.getBrandingLogoStorageKey()).isEqualTo("tenants/x/logo");
        verify(audit).record(eq(owner), eq(tenantId), eq("TENANT_BRANDING_UPDATED"), eq("UPDATE"), eq("TENANT_SETTINGS"),
                eq("TENANT"), eq(tenantId), eq(PlatformAuditWriter.SUCCESS), anyMap());
    }

    @Test
    void theRealContentDecidesTheType_notTheName() {
        assertThat(TenantBrandingService.checkLogo(PNG)).isEqualTo("image/png");
        assertThat(TenantBrandingService.checkLogo(SVG)).isEqualTo("image/svg+xml");
        assertThatThrownBy(() -> TenantBrandingService.checkLogo("GIF89a....".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class).hasMessageContaining("PNG or SVG");
    }

    @Test
    void anSvgThatCanRunScriptOrLoadOutsideContentIsRefused() {
        for (String bad : new String[] {
                "<svg><script>alert(1)</script></svg>",
                "<svg onload=\"alert(1)\"></svg>",
                "<svg><a href=\"javascript:alert(1)\">x</a></svg>",
                "<svg><foreignObject><div/></foreignObject></svg>",
                "<svg><image href=\"https://evil.test/x.png\"/></svg>"}) {
            assertThatThrownBy(() -> TenantBrandingService.checkLogo(bad.getBytes(StandardCharsets.UTF_8)))
                    .as(bad).isInstanceOf(ApiException.class).hasMessageContaining("scripts or outside links");
        }
    }

    @Test
    void aLogoOver512KbIsRefused() {
        byte[] big = Arrays.copyOf(PNG, 512 * 1024 + 1);
        assertThatThrownBy(() -> TenantBrandingService.checkLogo(big))
                .isInstanceOf(ApiException.class).hasMessageContaining("512 KB");
    }

    @Test
    void coloursNeedAPlanThatAllowsThem() {
        planAllowsColors(false);
        assertThatThrownBy(() -> service.update(tenantId, null, "#1A2B3C", null, false, owner))
                .isInstanceOf(ApiException.class).hasMessageContaining("does not include brand colours");
        verify(tenants, never()).save(any());
    }

    @Test
    void coloursAreSavedOnAPlanThatAllowsThem_andMustBeHex() {
        planAllowsColors(true);
        TenantBrandingService.Branding result = service.update(tenantId, null, "#1a2b3c", "#ABCDEF", false, owner);
        assertThat(result.primary()).isEqualTo("#1A2B3C");
        assertThat(result.accent()).isEqualTo("#ABCDEF");
        assertThat(tenant.getBrandingColors()).isEqualTo("{\"primary\":\"#1A2B3C\",\"accent\":\"#ABCDEF\"}");

        assertThatThrownBy(() -> service.update(tenantId, null, "red", null, false, owner))
                .isInstanceOf(ApiException.class).hasMessageContaining("#1A2B3C");
    }

    @Test
    void clearingColoursIsAlwaysAllowed_evenAfterAPlanDowngrade() {
        tenant.setBrandingColors("{\"primary\":\"#1A2B3C\"}");
        planAllowsColors(false);
        TenantBrandingService.Branding result = service.update(tenantId, null, "", null, false, owner);
        assertThat(result.primary()).isNull();
        assertThat(tenant.getBrandingColors()).isNull();
    }

    @Test
    void schoolUsersReadOnlyTheirOwnTenantsBranding() {
        TenantBrandingReadController controller = new TenantBrandingReadController(service);
        Authentication otherTenant = mock(Authentication.class);
        when(otherTenant.getPrincipal()).thenReturn(new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                UserType.STAFF, "Someone", false));
        assertThatThrownBy(() -> controller.branding(tenantId, otherTenant)).isInstanceOf(UnauthorizedException.class);

        Authentication own = mock(Authentication.class);
        when(own.getPrincipal()).thenReturn(new TenantPrincipal(tenantId, UUID.randomUUID(), UserType.STAFF, "Me", false));
        planAllowsColors(false);
        assertThat(controller.branding(tenantId, own).getBody().hasLogo()).isFalse();
    }
}
