import type { IncomingMessage, ServerResponse } from "node:http";
import { defineConfig, mergeConfig, type Plugin, type ProxyOptions } from "vite";
import baseConfig from "./vite.config";

/**
 * TEMPORARY: a second dev server (port 5174) for sharing the platform console AND every tenant through
 * ONE Cloudflare quick tunnel. Started by ..\dev-host.ps1. The normal dev server (vite.config.ts,
 * port 5173) and production builds never load this file.
 *
 * How one link serves every tenant:
 * - <link>/__tenant           picker page: "Platform console" or type any tenant subdomain;
 * - <link>/__tenant/<name>    remember <name> ("platform" or a tenant subdomain) in a cookie for
 *                             this browser, clear the old session, and open the sign-in page;
 * - every /api and /v1/ws call is forwarded to the local backend presenting
 *   <name>.edu360.local:8080, so the backend picks that realm exactly as it does locally.
 * A newly created tenant works at once (no restart). One browser = one choice at a time; use a
 * private/incognito window for a second tenant at the same time.
 * Delete this file when the tunnel is no longer needed. Never use it in production.
 */
const COOKIE = "edu360_tunnel_host";
const LABEL = /^[a-z0-9-]{1,63}$/;
const defaultLabel = (process.env.TUNNEL_TENANT || "platform").trim().toLowerCase();
if (!LABEL.test(defaultLabel)) {
  throw new Error(`TUNNEL_TENANT must be "platform" or a tenant subdomain, got "${defaultLabel}".`);
}

function chosenLabel(cookieHeader: string | string[] | undefined): string {
  const header = Array.isArray(cookieHeader) ? cookieHeader.join("; ") : cookieHeader ?? "";
  const match = new RegExp(`(?:^|;\\s*)${COOKIE}=([a-z0-9-]{1,63})(?:;|$)`).exec(header);
  return match ? match[1] : defaultLabel;
}

const presentAs: ProxyOptions["configure"] = (proxy) => {
  // The browser sees page and API on one origin, so this is not a cross-origin call. Drop the
  // tunnel Origin so the backend's CORS list (local dev addresses only) need not be widened.
  proxy.on("proxyReq", (proxyReq, req) => {
    proxyReq.setHeader("host", `${chosenLabel(req.headers.cookie)}.edu360.local:8080`);
    proxyReq.removeHeader("origin");
  });
  proxy.on("proxyReqWs", (proxyReq, req) => {
    proxyReq.setHeader("host", `${chosenLabel(req.headers.cookie)}.edu360.local:8080`);
    proxyReq.removeHeader("origin");
  });
};

function page(res: ServerResponse, status: number, body: string, headers: Record<string, string> = {}) {
  res.writeHead(status, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", ...headers });
  res.end(`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>EDU 360 · choose address</title>
<style>body{font:16px/1.5 system-ui,sans-serif;max-width:440px;margin:48px auto;padding:0 16px;color:#1d2a24;background:#f6f7f5}
h1{font-size:22px;margin:0 0 4px}p{color:#5b665f;margin:0 0 20px}a.btn,button{display:block;width:100%;box-sizing:border-box;padding:12px 16px;border-radius:10px;border:1px solid #c9d3cd;background:#fff;color:#1d2a24;font:inherit;text-align:left;text-decoration:none;cursor:pointer}
a.btn{margin-bottom:12px}button{background:#1f5c45;color:#fff;border-color:#1f5c45;text-align:center;margin-top:8px}
input{width:100%;box-sizing:border-box;padding:12px 14px;border-radius:10px;border:1px solid #c9d3cd;font:inherit}
label{display:block;font-weight:600;margin:16px 0 6px}small{color:#5b665f}.now{padding:8px 12px;background:#e7efe9;border-radius:8px;margin-bottom:20px}
@media (prefers-color-scheme:dark){body{background:#151b18;color:#e6ece8}p,small{color:#a6b2ab}a.btn{background:#1f2823;color:#e6ece8;border-color:#34413a}input{background:#1f2823;color:#e6ece8;border-color:#34413a}.now{background:#24302a}}</style>
</head><body>${body}</body></html>`);
}

/** /__tenant picker and /__tenant/<name> switch. Runs before Vite's own pages. */
const tenantSwitch: Plugin = {
  name: "edu360-tunnel-tenant-switch",
  configureServer(server) {
    server.middlewares.use((req: IncomingMessage, res: ServerResponse, next: () => void) => {
      const url = new URL(req.url ?? "/", "http://tunnel.local");
      if (url.pathname !== "/__tenant" && !url.pathname.startsWith("/__tenant/")) return next();

      const requested = (url.pathname === "/__tenant/go" ? url.searchParams.get("name") ?? "" : url.pathname.slice("/__tenant/".length))
        .trim().toLowerCase();
      if (url.pathname === "/__tenant" || url.pathname === "/__tenant/") {
        const current = chosenLabel(req.headers.cookie);
        return page(res, 200, `<h1>Choose where to sign in</h1><p>This link serves the platform console and every tenant.</p>
<div class="now">Now: <strong>${current === "platform" ? "Platform console" : `tenant ${current}`}</strong></div>
<a class="btn" href="/__tenant/platform"><strong>Platform console</strong><br><small>Product Owner, Platform Admin and other platform staff</small></a>
<form action="/__tenant/go" method="get"><label for="name">Tenant subdomain</label>
<input id="name" name="name" placeholder="e.g. abc" autocomplete="off" autocapitalize="none" pattern="[a-zA-Z0-9-]{1,63}" required>
<small>The part before .edu360 in the tenant's address. New tenants work at once.</small>
<button type="submit">Open tenant sign-in</button></form>`);
      }
      if (!LABEL.test(requested)) {
        return page(res, 400, `<h1>That is not a tenant subdomain</h1><p>Use letters, digits and hyphens only, e.g. abc.</p><a class="btn" href="/__tenant">Back</a>`);
      }
      // Remember the choice for this browser, end any session from the previous address (tokens
      // live in sessionStorage per tab), then go to sign-in.
      return page(res, 200, `<p>Opening ${requested === "platform" ? "the platform console" : `tenant ${requested}`}…</p>
<script>try{sessionStorage.clear()}catch(e){}location.replace("/login")</script>`, {
        "Set-Cookie": `${COOKIE}=${requested}; Path=/; SameSite=Lax; Max-Age=2592000`,
      });
    });
  },
};

export default mergeConfig(baseConfig, defineConfig({
  plugins: [tenantSwitch],
  server: {
    port: 5174,
    strictPort: true,
    allowedHosts: [".trycloudflare.com"],
    proxy: {
      "/api": { target: "http://127.0.0.1:8080", configure: presentAs },
      "/v1/ws": { target: "ws://127.0.0.1:8080", ws: true, configure: presentAs },
    },
  },
}));
