const INSTITUTION_PREF_KEY = "edu360_institution";
const TUNNEL_COOKIE_KEY = "edu360_tunnel_host";
const SLUG_PATTERN = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
const COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60; // 30 days

const KNOWN_SHARED_HOSTS = new Set([
  "localhost",
  "127.0.0.1",
  "[::1]",
  "edu360.localhost",
  "www.edu360.localhost",
  "edu360.local",
  "www.edu360.local",
  "edu360.com",
  "www.edu360.com",
  "edu360.duckdns.org",
  "www.edu360.duckdns.org",
]);

export function normalizeInstitutionSlug(raw: string): string {
  return raw.trim().toLowerCase();
}

export function isValidInstitutionSlug(slug: string): boolean {
  return SLUG_PATTERN.test(slug);
}

/**
 * Returns true when running behind a single-hostname Cloudflare quick tunnel
 * (dev-host.ps1 / vite.tunnel.config.ts), where tenant switching uses /__tenant/<slug>
 * instead of subdomains.
 */
export function isTunnelHost(hostname: string): boolean {
  return hostname.trim().toLowerCase().endsWith(".trycloudflare.com");
}

/**
 * Returns true only when the current hostname is the shared public/landing host
 * (e.g. edu360.com, www.edu360.com, localhost, edu360.localhost, or *.trycloudflare.com),
 * and false for any tenant subdomain (<slug>.edu360.com) or platform host (platform.edu360.com).
 */
export function isSharedLandingHost(hostname: string): boolean {
  const host = hostname.trim().toLowerCase();
  if (!host) return false;
  if (isTunnelHost(host)) return true;
  if (KNOWN_SHARED_HOSTS.has(host)) return true;

  const configuredLanding = (import.meta.env.VITE_LANDING_HOST || "").trim().toLowerCase();
  if (configuredLanding) {
    const bare = configuredLanding.startsWith("www.") ? configuredLanding.slice(4) : configuredLanding;
    return host === bare || host === `www.${bare}`;
  }

  const parts = host.split(".");
  if (host.endsWith(".duckdns.org")) {
    return false;
  }
  return parts.length === 2 || (parts.length === 3 && parts[0] === "www");
}

/**
 * Builds the target login URL on the institution's (or platform's) real subdomain
 * so backend tenant resolution continues to work strictly from the Host header.
 * For Cloudflare quick tunnels (*.trycloudflare.com), routes via /__tenant/<slug>
 * handled by vite.tunnel.config.ts.
 */
export function buildTargetLoginUrl(
  rawSlug: string,
  location: Pick<Location, "protocol" | "hostname" | "port">,
): string {
  const slug = normalizeInstitutionSlug(rawSlug);
  if (!isValidInstitutionSlug(slug)) {
    throw new Error("Invalid institution code");
  }

  const host = location.hostname.toLowerCase();
  const port = location.port ? `:${location.port}` : "";

  if (isTunnelHost(host)) {
    return `${location.protocol}//${host}${port}/__tenant/${slug}`;
  }

  let targetHost: string;
  if (host === "localhost" || host === "127.0.0.1" || host === "[::1]") {
    targetHost = `${slug}.edu360.localhost`;
  } else if (host.endsWith(".edu360.localhost")) {
    targetHost = `${slug}.edu360.localhost`;
  } else if (host.endsWith(".edu360.local")) {
    targetHost = `${slug}.edu360.local`;
  } else if (
    host === "edu360.duckdns.org" ||
    host === "www.edu360.duckdns.org" ||
    host.endsWith("-edu360.duckdns.org")
  ) {
    targetHost = `${slug}-edu360.duckdns.org`;
  } else if (host.startsWith("platform.")) {
    targetHost = `${slug}.${host.slice("platform.".length)}`;
  } else if (host.startsWith("platform-")) {
    targetHost = `${slug}-${host.slice("platform-".length)}`;
  } else {
    const baseHost = host.startsWith("www.") ? host.slice(4) : host;
    const parts = baseHost.split(".");
    if (parts.length >= 3) {
      targetHost = `${slug}.${parts.slice(1).join(".")}`;
    } else {
      targetHost = `${slug}.${baseHost}`;
    }
  }

  return `${location.protocol}//${targetHost}${port}/login`;
}

/**
 * Builds the URL back to the shared landing page with ?switch=1 so a user on a
 * tenant or platform subdomain (or tunnel) can switch institutions without auto-redirecting.
 */
export function buildSharedLandingUrl(
  location: Pick<Location, "protocol" | "hostname" | "port">,
): string {
  const host = location.hostname.toLowerCase();
  const port = location.port ? `:${location.port}` : "";

  if (isTunnelHost(host)) {
    return `${location.protocol}//${host}${port}/?switch=1`;
  }

  let landingHost: string;
  const configuredLanding = (import.meta.env.VITE_LANDING_HOST || "").trim().toLowerCase();
  if (configuredLanding) {
    landingHost = configuredLanding.startsWith("www.") ? configuredLanding.slice(4) : configuredLanding;
  } else if (host.endsWith(".edu360.localhost")) {
    landingHost = "localhost";
  } else if (host.endsWith("-edu360.duckdns.org")) {
    landingHost = "edu360.duckdns.org";
  } else {
    const parts = host.split(".");
    landingHost = parts.length > 2 ? parts.slice(1).join(".") : host;
  }

  return `${location.protocol}//${landingHost}${port}/?switch=1`;
}

function readCookieValue(cookieHeader: string, key: string): string | null {
  if (!cookieHeader) return null;
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = new RegExp(`(?:^|;\\s*)${escaped}=([^;]+)`).exec(cookieHeader);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Resolves the active institution slug (or "platform") from the current hostname or
 * Cloudflare tunnel cookie. Returns null on a bare shared landing host.
 */
export function getActiveInstitutionSlug(hostname: string, cookieHeader = ""): string | null {
  const host = hostname.trim().toLowerCase();
  if (!host) return null;

  if (isTunnelHost(host)) {
    const tunnelSlug = readCookieValue(cookieHeader, TUNNEL_COOKIE_KEY);
    if (!tunnelSlug) return "platform";
    const normalized = normalizeInstitutionSlug(tunnelSlug);
    return isValidInstitutionSlug(normalized) ? normalized : null;
  }

  if (isSharedLandingHost(host)) return null;

  if (host.endsWith("-edu360.duckdns.org")) {
    const label = host.slice(0, -"-edu360.duckdns.org".length);
    return isValidInstitutionSlug(label) ? label : null;
  }

  const firstDot = host.indexOf(".");
  if (firstDot <= 0) return null;
  const label = host.slice(0, firstDot);
  return isValidInstitutionSlug(label) ? label : null;
}

function getParentCookieDomain(hostname: string): string | null {
  const host = hostname.trim().toLowerCase();
  if (!host || host === "localhost" || host === "127.0.0.1" || host === "[::1]") return null;
  if (isTunnelHost(host) || host.endsWith(".duckdns.org")) return null;
  const parts = host.split(".");
  if (parts.length < 3) return null;
  return `.${parts.slice(1).join(".")}`;
}

export function getRememberedInstitution(): string | null {
  try {
    const stored = localStorage.getItem(INSTITUTION_PREF_KEY);
    if (stored) {
      const normalized = normalizeInstitutionSlug(stored);
      if (isValidInstitutionSlug(normalized)) return normalized;
    }
  } catch {
    // Fall through to cookie check
  }

  try {
    if (typeof document !== "undefined") {
      const fromCookie = readCookieValue(document.cookie, INSTITUTION_PREF_KEY);
      if (fromCookie) {
        const normalized = normalizeInstitutionSlug(fromCookie);
        return isValidInstitutionSlug(normalized) ? normalized : null;
      }
    }
  } catch {
    // Ignore storage errors
  }

  return null;
}

export function setRememberedInstitution(rawSlug: string): void {
  const slug = normalizeInstitutionSlug(rawSlug);
  if (!isValidInstitutionSlug(slug)) return;
  try {
    localStorage.setItem(INSTITUTION_PREF_KEY, slug);
  } catch {
    // Graceful fallback if storage is disabled
  }
}

/**
 * Called ONLY after login succeeds. Saves the non-sensitive public institution slug
 * to localStorage (for same-origin hosts like Cloudflare tunnels) and, when on a
 * subdomain of a shared domain (e.g. abc.edu360.com), sets a first-party Domain
 * cookie so edu360.com can remember the institution on the next visit.
 */
export function rememberInstitutionOnLoginSuccess(
  location: Pick<Location, "protocol" | "hostname">,
  cookieHeader = typeof document !== "undefined" ? document.cookie : "",
): void {
  const slug = getActiveInstitutionSlug(location.hostname, cookieHeader);
  if (!slug) return;

  setRememberedInstitution(slug);

  if (typeof document === "undefined") return;
  try {
    const parentDomain = getParentCookieDomain(location.hostname);
    if (!parentDomain) return;
    const secure = location.protocol === "https:" ? "; Secure" : "";
    document.cookie = `${INSTITUTION_PREF_KEY}=${encodeURIComponent(slug)}; Domain=${parentDomain}; Path=/; SameSite=Lax; Max-Age=${COOKIE_MAX_AGE_SECONDS}${secure}`;
  } catch {
    // Graceful fallback if cookies are blocked
  }
}

export function clearRememberedInstitution(): void {
  try {
    localStorage.removeItem(INSTITUTION_PREF_KEY);
  } catch {
    // Graceful fallback if storage is disabled
  }

  if (typeof document === "undefined" || typeof window === "undefined") return;
  try {
    const secure = window.location.protocol === "https:" ? "; Secure" : "";
    document.cookie = `${INSTITUTION_PREF_KEY}=; Path=/; SameSite=Lax; Max-Age=0${secure}`;
    const parentDomain = getParentCookieDomain(window.location.hostname);
    if (parentDomain) {
      document.cookie = `${INSTITUTION_PREF_KEY}=; Domain=${parentDomain}; Path=/; SameSite=Lax; Max-Age=0${secure}`;
    }
  } catch {
    // Graceful fallback
  }
}

export function isBackForwardNavigation(): boolean {
  try {
    if (typeof performance === "undefined" || typeof performance.getEntriesByType !== "function") {
      return false;
    }
    const entries = performance.getEntriesByType("navigation") as PerformanceNavigationTiming[];
    return entries.length > 0 && entries[0].type === "back_forward";
  } catch {
    return false;
  }
}
