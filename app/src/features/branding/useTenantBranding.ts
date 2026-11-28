import { useEffect, useState } from "react";
import api from "../../services/axios";

type Branding = { hasLogo: boolean; logoVersion: string | null; primary: string | null; accent: string | null };

const HEX = /^#[0-9A-Fa-f]{6}$/;

interface CachedBranding {
  data: Branding;
  objectUrl: string | null;
}

const brandingCache = new Map<string, CachedBranding>();
const pendingFetches = new Map<string, Promise<CachedBranding | null>>();

export function clearBrandingCache(tenantId?: string) {
  if (tenantId) {
    const cached = brandingCache.get(tenantId);
    if (cached?.objectUrl) {
      try { URL.revokeObjectURL(cached.objectUrl); } catch { /* ignore */ }
    }
    brandingCache.delete(tenantId);
    pendingFetches.delete(tenantId);
  } else {
    for (const cached of brandingCache.values()) {
      if (cached.objectUrl) {
        try { URL.revokeObjectURL(cached.objectUrl); } catch { /* ignore */ }
      }
    }
    brandingCache.clear();
    pendingFetches.clear();
  }
}

async function fetchBranding(tenantId: string): Promise<CachedBranding | null> {
  try {
    const { data } = await api.get<Branding>(`/tenants/${tenantId}/branding`);
    let objectUrl: string | null = null;
    if (data.hasLogo) {
      try {
        const res = await api.get<Blob>(`/tenants/${tenantId}/branding/logo`, { responseType: "blob" });
        objectUrl = URL.createObjectURL(res.data);
      } catch {
        /* Logo fetch failure - keep objectUrl null */
      }
    }
    const cached: CachedBranding = { data, objectUrl };
    brandingCache.set(tenantId, cached);
    return cached;
  } catch {
    /* No branding or not reachable */
    return null;
  } finally {
    pendingFetches.delete(tenantId);
  }
}

// E1-S4: a signed-in tenant user's app shell shows the tenant logo and brand colours. Colours are
// applied by overriding the existing design-system tokens (never new ones), and removed again when
// the user leaves the tenant (sign-out, platform console). Any failure just keeps EDU 360's look.
// Branding is cached per session keyed by tenantId so remounting does not refetch.
export function useTenantBranding(tenantId: string | null | undefined): { logoUrl: string | null } {
  const [logoUrl, setLogoUrl] = useState<string | null>(() => {
    if (!tenantId) return null;
    return brandingCache.get(tenantId)?.objectUrl ?? null;
  });

  useEffect(() => {
    if (!tenantId) {
      void Promise.resolve().then(() => setLogoUrl(null));
      return undefined;
    }

    let active = true;
    const root = document.documentElement;
    const applied: string[] = [];

    const applyBranding = (cached: CachedBranding) => {
      const { data, objectUrl } = cached;
      if (data.primary && HEX.test(data.primary)) {
        root.style.setProperty("--color-action-primary", data.primary);
        applied.push("--color-action-primary");
        const hover = `color-mix(in srgb, ${data.primary} 85%, black)`;
        root.style.setProperty("--color-action-primary-hover", hover);
        applied.push("--color-action-primary-hover");
      }
      if (data.accent && HEX.test(data.accent)) {
        root.style.setProperty("--color-accent", data.accent);
        applied.push("--color-accent");
      }
      if (active) {
        setLogoUrl(objectUrl);
      }
    };

    const cached = brandingCache.get(tenantId);
    if (cached) {
      applyBranding(cached);
    } else {
      let fetchPromise = pendingFetches.get(tenantId);
      if (!fetchPromise) {
        fetchPromise = fetchBranding(tenantId);
        pendingFetches.set(tenantId, fetchPromise);
      }
      fetchPromise.then((result) => {
        if (!active || !result) return;
        applyBranding(result);
      });
    }

    return () => {
      active = false;
      applied.forEach((name) => root.style.removeProperty(name));
      setLogoUrl(null);
    };
  }, [tenantId]);

  return { logoUrl };
}

