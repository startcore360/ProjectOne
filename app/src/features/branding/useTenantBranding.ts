import { useEffect, useState } from "react";
import api from "../../services/axios";

type Branding = { hasLogo: boolean; logoVersion: string | null; primary: string | null; accent: string | null };

const HEX = /^#[0-9A-Fa-f]{6}$/;

// E1-S4: a signed-in tenant user's app shell shows the tenant logo and brand colours. Colours are
// applied by overriding the existing design-system tokens (never new ones), and removed again when
// the user leaves the tenant (sign-out, platform console). Any failure just keeps EDU 360's look.
export function useTenantBranding(tenantId: string | null | undefined): { logoUrl: string | null } {
  const [logoUrl, setLogoUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!tenantId) return undefined;
    let active = true;
    let objectUrl: string | null = null;
    const root = document.documentElement;
    const applied: string[] = [];
    const setToken = (name: string, value: string) => {
      root.style.setProperty(name, value);
      applied.push(name);
    };

    api
      .get<Branding>(`/tenants/${tenantId}/branding`)
      .then(async ({ data }) => {
        if (!active) return;
        if (data.primary && HEX.test(data.primary)) {
          setToken("--color-action-primary", data.primary);
          setToken("--color-action-primary-hover", `color-mix(in srgb, ${data.primary} 85%, black)`);
        }
        if (data.accent && HEX.test(data.accent)) {
          setToken("--color-accent", data.accent);
        }
        if (data.hasLogo) {
          const res = await api.get<Blob>(`/tenants/${tenantId}/branding/logo`, { responseType: "blob" });
          if (!active) return;
          objectUrl = URL.createObjectURL(res.data);
          setLogoUrl(objectUrl);
        }
      })
      .catch(() => {
        /* No branding, or not reachable: the standard EDU 360 look stays. */
      });

    return () => {
      active = false;
      applied.forEach((name) => root.style.removeProperty(name));
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      setLogoUrl(null);
    };
  }, [tenantId]);

  return { logoUrl };
}
