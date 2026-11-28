import { defineConfig } from "vitest/config";
import path from "node:path";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
  host: "0.0.0.0",
  port: 5173,
  strictPort: true,
  // The leading dot allows every subdomain, so a new tenant needs no change here.
  // ".edu360.localhost" is the recommended local address: browsers send every *.localhost name to
  // 127.0.0.1 by themselves, so a new tenant opens with no hosts-file step.
  // ".edu360.local" still works for anyone using the hosts file (docker/dev-hosts-setup.ps1).
  // ".duckdns.org" covers abc-edu360.duckdns.org (one DuckDNS name per tenant, for testing from
  // outside this machine).
  //
  // Dev server only. PRODUCTION does not use Vite: the built files are served behind a reverse
  // proxy, reached through a wildcard DNS record (*.edu360.com) and a wildcard TLS certificate.
  // See docker/PRODUCTION_TENANT_ROUTING.txt.
  allowedHosts: [".edu360.localhost", ".edu360.local", ".duckdns.org"],
},
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules")) {
            if (id.includes("react") || id.includes("react-dom") || id.includes("react-router")) {
              return "vendor-react";
            }
            if (id.includes("@reduxjs") || id.includes("react-redux")) {
              return "vendor-redux";
            }
            if (id.includes("axios")) {
              return "vendor-axios";
            }
          }
          if (id.includes("JsonDocumentBuilder") || (id.includes("institution-admin") && (id.includes("components/academic") || id.includes("components\\academic")))) {
            return "institution-academic-modals";
          }
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.tsx",
    css: true,
    pool: "threads",
  },
});
