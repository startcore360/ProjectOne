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
  // Dev server only. Production is covered in docker/PRODUCTION_TENANT_ROUTING.txt.
  allowedHosts: [".edu360.local"],
},
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.tsx",
    css: true,
    pool: "threads",
  },
});
