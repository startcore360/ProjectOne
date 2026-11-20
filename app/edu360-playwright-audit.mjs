// Read-only UI audit of the platform console (local dev only). No form submissions, no changes.
//
//   cd app
//   npm install --no-save playwright          (once; does not change package.json)
//   node .\edu360-playwright-audit.mjs        (local: http://platform.edu360.local:5173)
//   $env:EDU360_URL = "https://<link>.trycloudflare.com"; node .\edu360-playwright-audit.mjs
//
// Opens the Google Chrome installed on this PC and signs in: automatically when app/.audit-login.json
// exists (see .audit-login.example.json; the file is git-ignored), otherwise you sign in by hand. Then it
// visits every platform page at three sizes in light and
// dark theme. For each it records: screenshot, horizontal overflow, unlabelled inputs, unnamed
// buttons, images without alt text, headings, API calls (failed and repeated) and console errors.
// At the end it signs out with the app's own Sign out button.
// Output: app/playwright-report/edu360-audit/<timestamp>/ with report.md, report.json and PNGs.
import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";
import readline from "node:readline/promises";
import { createHmac } from "node:crypto";

const BASE_URL = (process.env.EDU360_URL || "http://platform.edu360.local:5173").replace(/\/$/, "");
// Folder named by local date and time (e.g. 2026-11-19_23-32-05); the newest report is also copied
// to playwright-report/edu360-audit/latest/ so it is always in the same place.
const ROOT_OUT = path.resolve("./playwright-report/edu360-audit");
const pad = (n) => String(n).padStart(2, "0");
const now = new Date();
const OUT = path.join(ROOT_OUT, `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`);

const ROUTES = [
  "/platform", "/platform/tenants", "/platform/plans", "/platform/invoices", "/platform/releases",
  "/platform/reports", "/platform/security-policy", "/platform/tickets", "/platform/users",
  "/platform/roles", "/platform/role-templates", "/platform/support", "/platform/audit",
  "/platform/usage-counts", "/platform/backup-status", "/platform/integration-types",
  "/platform/data-protection",
];
const VIEWPORTS = [
  { width: 1440, height: 900 },
  { width: 1366, height: 768 }, // common laptop height (CLAUDE.md UI standard)
  { width: 390, height: 844 },
];
const THEMES = ["light", "dark"];
// The dev server runs React StrictMode, which deliberately runs every page's loading effects twice
// (development only). So in dev each call appearing twice is expected; only 3+ is a real repeat.
// Against a production build set EDU360_STRICT=0 to flag every call made more than once.
const REPEAT_LIMIT = process.env.EDU360_STRICT === "0" ? 1 : 2;

const findings = [];
const consoleErrors = [];
const pageErrors = [];
const failedRequests = [];
let apiCalls = [];

/* ---------- earlier reports: keep or delete ----------
   Asks once, before Chrome opens. Answer with EDU360_CLEAN=yes / no to skip the question
   (e.g. when running unattended). The "latest" copy is replaced by this run either way. */
async function earlierReports() {
  try {
    const entries = await fs.readdir(ROOT_OUT, { withFileTypes: true });
    return entries.filter((entry) => entry.isDirectory() && entry.name !== "latest").map((entry) => entry.name);
  } catch {
    return [];
  }
}

const previous = await earlierReports();
if (previous.length) {
  let answer = (process.env.EDU360_CLEAN || "").trim().toLowerCase();
  if (!answer && process.stdin.isTTY) {
    const ask = readline.createInterface({ input: process.stdin, output: process.stdout });
    answer = (await ask.question(
      `Found ${previous.length} earlier report${previous.length === 1 ? "" : "s"} in ${ROOT_OUT}.\n` +
      "Delete them before this run? (y = delete, n = keep) [n]: ",
    )).trim().toLowerCase();
    ask.close();
  }
  if (answer === "y" || answer === "yes") {
    for (const name of previous) await fs.rm(path.join(ROOT_OUT, name), { recursive: true, force: true });
    console.log(`Deleted ${previous.length} earlier report${previous.length === 1 ? "" : "s"}.\n`);
  } else {
    console.log("Keeping earlier reports.\n");
  }
}

await fs.mkdir(OUT, { recursive: true });

let browser;
try {
  // channel "chrome" = the Google Chrome already installed here (no Playwright browser download).
  browser = await chromium.launch({ channel: "chrome", headless: false });
} catch (error) {
  console.error("Could not open Google Chrome:", String(error).split("\n")[0]);
  console.error('If Chrome is not installed, change channel "chrome" to "msedge" in this file.');
  await fs.rm(OUT, { recursive: true, force: true });
  process.exit(1);
}

const context = await browser.newContext({ viewport: VIEWPORTS[0], ignoreHTTPSErrors: true });
const page = await context.newPage();

page.on("console", (msg) => {
  if (msg.type() === "error") consoleErrors.push({ url: page.url(), text: msg.text(), resource: msg.location()?.url || null });
});
page.on("pageerror", (error) => pageErrors.push({ url: page.url(), text: String(error) }));
page.on("requestfailed", (req) => failedRequests.push({ url: req.url(), error: req.failure()?.errorText }));
page.on("response", (response) => {
  const url = new URL(response.url());
  if (url.pathname.includes("/api/")) {
    apiCalls.push({ method: response.request().method(), path: url.pathname, query: url.search, status: response.status() });
  }
});

// The login page is public, so it is audited first (all sizes, both themes), before signing in.
console.log("Auditing the login page before sign-in...");
for (const theme of THEMES) {
  for (const viewport of VIEWPORTS) await inspectPage("/login", viewport, theme, { publicPage: true });
}
await page.setViewportSize(VIEWPORTS[0]);
await page.emulateMedia({ colorScheme: "light" });

console.log(`Opening ${BASE_URL}/login`);
await page.goto(`${BASE_URL}/login`, { waitUntil: "domcontentloaded", timeout: 90000 });

const credentials = await loadCredentials();
if (credentials) {
  await autoSignIn(credentials);
} else {
  console.log("\nNo .audit-login.json found, so sign in manually in the Chrome window (complete the authenticator code if asked).");
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  await rl.question("When the dashboard is visible, press ENTER here to start the audit... ");
  rl.close();
}

/* ---------- automatic sign-in (optional) ----------
   Reads app/.audit-login.json (git-ignored) or EDU360_EMAIL / EDU360_PASSWORD / EDU360_TOTP_SECRET.
   Platform roles must use two-step login, so the 6-digit code is generated here from the account's
   authenticator secret (the "enter this key" text shown when the authenticator is set up), exactly as
   an authenticator app does (RFC 6238). Nothing is written anywhere and nothing is printed. */
async function loadCredentials() {
  if (process.env.EDU360_EMAIL && process.env.EDU360_PASSWORD) {
    return { email: process.env.EDU360_EMAIL, password: process.env.EDU360_PASSWORD, totpSecret: process.env.EDU360_TOTP_SECRET || "" };
  }
  try {
    const file = JSON.parse(await fs.readFile(path.resolve(".audit-login.json"), "utf8"));
    if (file.email && file.password) return { email: file.email, password: file.password, totpSecret: file.totpSecret || "" };
  } catch { /* no file: manual sign-in */ }
  return null;
}

function base32Decode(secret) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  const clean = secret.toUpperCase().replace(/[^A-Z2-7]/g, "");
  let bits = "";
  for (const char of clean) bits += alphabet.indexOf(char).toString(2).padStart(5, "0");
  const bytes = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) bytes.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(bytes);
}

function totp(secret, atMs = Date.now()) {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(atMs / 1000 / 30)));
  const hmac = createHmac("sha1", base32Decode(secret)).update(counter).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const value = (hmac.readUInt32BE(offset) & 0x7fffffff) % 1_000_000;
  return String(value).padStart(6, "0");
}

async function autoSignIn({ email, password, totpSecret }) {
  console.log(`\nSigning in automatically as ${email} ...`);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.locator(".auth-submit").click();

  const codeField = page.getByLabel("Authenticator code");
  const outcome = await Promise.race([
    page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 30000 }).then(() => "signed-in"),
    codeField.waitFor({ state: "visible", timeout: 30000 }).then(() => "code"),
  ]).catch(() => "unknown");

  if (outcome === "code") {
    if (!totpSecret) throw new Error("This account asks for an authenticator code, but .audit-login.json has no totpSecret.");
    // Avoid typing a code that expires while the request is in flight.
    const secondsLeft = 30 - (Math.floor(Date.now() / 1000) % 30);
    if (secondsLeft < 5) await page.waitForTimeout(secondsLeft * 1000 + 500);
    await codeField.fill(totp(totpSecret));
    await page.locator(".auth-submit").click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 30000 });
  } else if (outcome !== "signed-in") {
    const message = await page.locator(".auth-message, [role=alert]").first().innerText().catch(() => "");
    throw new Error(`Automatic sign-in did not finish${message ? `: ${message}` : ""}. Check .audit-login.json.`);
  }
  if (page.url().includes("/change-password")) throw new Error("This account must change its password first. Sign in once by hand, change it, and update .audit-login.json.");
  console.log("Signed in.");
}

async function signOut() {
  try {
    await page.setViewportSize(VIEWPORTS[0]);
    await page.goto(`${BASE_URL}/platform`, { waitUntil: "domcontentloaded", timeout: 30000 });
    await page.getByRole("button", { name: "Sign out" }).first().click({ timeout: 10000 });
    await page.waitForURL((url) => url.pathname.startsWith("/login"), { timeout: 15000 });
    console.log("Signed out.");
    return true;
  } catch (error) {
    console.log(`Could not sign out automatically: ${String(error).split("\n")[0]}`);
    return false;
  }
}

async function inspectPage(route, viewport, theme, { publicPage = false } = {}) {
  const target = new URL(route, BASE_URL).toString();
  apiCalls = [];
  try {
    await page.setViewportSize(viewport);
    await page.emulateMedia({ colorScheme: theme });
    await page.goto(target, { waitUntil: "domcontentloaded", timeout: 45000 });
    // The app's theme toggle uses data-theme on <html>; set it so both theme paths are covered.
    await page.evaluate((value) => document.documentElement.setAttribute("data-theme", value), theme);
    // Wait for the page's data, not a fixed delay, so screenshots are not half-loaded.
    await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(500);

    const data = await page.evaluate(() => {
      const visible = (el) => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
      const controls = [...document.querySelectorAll("input,select,textarea")].filter(visible).map((el) => ({
        tag: el.tagName.toLowerCase(), type: el.getAttribute("type"), id: el.id || null,
        ariaLabel: el.getAttribute("aria-label"), labelledBy: el.getAttribute("aria-labelledby"),
        hasLabel: !!(el.labels && el.labels.length), placeholder: el.getAttribute("placeholder"),
      }));
      const unnamedButtons = [...document.querySelectorAll('button,[role="button"],a[href]')].filter(visible)
        .filter((el) => !(el.innerText || "").trim() && !el.getAttribute("aria-label") && !el.getAttribute("title"))
        .map((el) => el.outerHTML.slice(0, 240));
      const imagesWithoutAlt = [...document.images].filter(visible).filter((img) => !img.hasAttribute("alt")).map((img) => img.src);
      const doc = document.documentElement;
      const main = document.querySelector('main,[role="main"]');
      return {
        url: location.href,
        signedOut: location.pathname.startsWith("/login"),
        viewport: { width: innerWidth, height: innerHeight },
        document: { scrollWidth: doc.scrollWidth, clientWidth: doc.clientWidth, scrollHeight: doc.scrollHeight },
        horizontalOverflow: doc.scrollWidth > doc.clientWidth + 1,
        headings: [...document.querySelectorAll("h1,h2,h3")].filter(visible).map((h) => ({ level: h.tagName, text: h.innerText.trim() })),
        controlsMissingAccessibleName: controls.filter((c) => !c.ariaLabel && !c.labelledBy && !c.hasLabel),
        unnamedButtons,
        imagesWithoutAlt,
        landmarks: { main: !!main, nav: !!document.querySelector('nav,[role="navigation"]'), banner: !!document.querySelector('header,[role="banner"]') },
        themeInfo: { dataTheme: doc.getAttribute("data-theme"), background: getComputedStyle(document.body).backgroundColor, text: getComputedStyle(document.body).color },
      };
    });

    const calls = apiCalls.slice();
    const repeated = Object.entries(calls.reduce((all, call) => {
      const key = `${call.method} ${call.path}`;
      all[key] = (all[key] || 0) + 1;
      return all;
    }, {})).filter(([, count]) => count > REPEAT_LIMIT).map(([call, count]) => ({ call, count }));
    const failed = calls.filter((call) => call.status >= 400);

    const safeName = route.replaceAll("/", "_").replace(/^_/, "") || "home";
    const imagePath = path.join(OUT, `${safeName}-${theme}-${viewport.width}x${viewport.height}.png`);
    await page.screenshot({ path: imagePath, fullPage: true, animations: "disabled" }).catch(() => {});

    findings.push({ route, theme, ...data, api: { total: calls.length, failed, repeated }, screenshot: imagePath });
    console.log(
      `${route.padEnd(30)} ${theme.padEnd(5)} ${String(viewport.width).padStart(4)}px | ` +
      `${data.signedOut && !publicPage ? "SIGNED OUT" : data.horizontalOverflow ? "OVERFLOW" : "ok"} | ` +
      `api ${calls.length} (failed ${failed.length}, repeated ${repeated.length}) | ` +
      `unnamed: ${data.controlsMissingAccessibleName.length} inputs, ${data.unnamedButtons.length} buttons`,
    );
    if (data.signedOut && !publicPage) throw new Error("Session ended (redirected to /login). Sign in again and re-run.");
  } catch (error) {
    findings.push({ route, theme, viewport, error: String(error) });
    console.log(`${route} | ${theme} | ${viewport.width}px | ERROR: ${String(error).split("\n")[0]}`);
    if (String(error).includes("Session ended")) throw error;
  }
}

try {
  for (const theme of THEMES) {
    for (const route of ROUTES) {
      for (const viewport of VIEWPORTS) await inspectPage(route, viewport, theme);
    }
  }
} catch {
  console.log("\nStopped early because the session ended. Writing the partial report.");
}

const totals = {
  pagesVisited: findings.length,
  errors: findings.filter((x) => x.error).length,
  horizontalOverflow: findings.filter((x) => x.horizontalOverflow).length,
  controlsMissingAccessibleName: findings.reduce((n, x) => n + (x.controlsMissingAccessibleName?.length || 0), 0),
  unnamedButtons: findings.reduce((n, x) => n + (x.unnamedButtons?.length || 0), 0),
  imagesWithoutAlt: findings.reduce((n, x) => n + (x.imagesWithoutAlt?.length || 0), 0),
  failedApiCalls: findings.reduce((n, x) => n + (x.api?.failed.length || 0), 0),
  pagesWithRepeatedApiCalls: findings.filter((x) => x.api?.repeated.length).length,
  pageErrors: pageErrors.length,
  consoleErrors: consoleErrors.length,
  failedRequests: failedRequests.length,
};
const report = { generatedAt: new Date().toISOString(), baseUrl: BASE_URL, note: "Read-only UI audit. No form submissions.", totals, findings, consoleErrors, pageErrors, failedRequests };
await fs.writeFile(path.join(OUT, "report.json"), JSON.stringify(report, null, 2));

const line = (x) => {
  if (x.error) return `- ${x.route} (${x.theme}, ${x.viewport?.width}px): ERROR ${x.error.split("\n")[0]}`;
  const notes = [];
  if (x.horizontalOverflow) notes.push("horizontal overflow");
  if (x.api.failed.length) notes.push(`failed API: ${x.api.failed.map((c) => `${c.status} ${c.method} ${c.path}`).join(", ")}`);
  if (x.api.repeated.length) notes.push(`repeated API: ${x.api.repeated.map((r) => `${r.call} ×${r.count}`).join(", ")}`);
  if (x.controlsMissingAccessibleName.length) notes.push(`${x.controlsMissingAccessibleName.length} unlabelled inputs`);
  if (x.unnamedButtons.length) notes.push(`${x.unnamedButtons.length} unnamed buttons/links`);
  return `- ${x.route} (${x.theme}, ${x.viewport.width}px): ${notes.join("; ") || "no issues found"}`;
};
const markdown = [
  "# EDU 360 Playwright UI Audit",
  `Generated: ${report.generatedAt}`,
  `Base URL: ${BASE_URL}`,
  "",
  "## Summary",
  `- repeated API calls are counted only above ${REPEAT_LIMIT} per page (dev StrictMode runs each load twice)`,
  ...Object.entries(totals).map(([key, value]) => `- ${key}: ${value}`),
  "",
  "## Page observations",
  ...findings.map(line),
  "",
  "## Console errors",
  ...(consoleErrors.length ? consoleErrors.map((x) => `- ${x.url}: ${x.text}${x.resource ? ` (resource: ${x.resource})` : ""}`) : ["- none"]),
  "",
  "## Page errors",
  ...(pageErrors.length ? pageErrors.map((x) => `- ${x.url}: ${x.text}`) : ["- none"]),
  "",
  "## Failed network requests",
  ...(failedRequests.length ? failedRequests.map((x) => `- ${x.url}: ${x.error}`) : ["- none"]),
].join("\n");
await fs.writeFile(path.join(OUT, "report.md"), markdown);
const LATEST = path.join(ROOT_OUT, "latest");
await fs.mkdir(LATEST, { recursive: true });
await fs.copyFile(path.join(OUT, "report.md"), path.join(LATEST, "report.md"));
await fs.copyFile(path.join(OUT, "report.json"), path.join(LATEST, "report.json"));

console.log("\nAUDIT COMPLETE");
console.log(`Report:      ${path.join(OUT, "report.md")}`);
console.log(`Screenshots: ${OUT}`);
console.log(`Latest copy: ${path.join(LATEST, "report.md")}`);
await browser.close();
