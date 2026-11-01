// Light/dark theme helpers. Remembers the user's choice in the browser's
// localStorage so it's still picked next time they open the app, and falls
// back to whatever the device's own light/dark setting is if they've never
// chosen one.
const STORAGE_KEY = "edu360-theme";

export type Theme = "light" | "dark";

function systemTheme(): Theme {
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function getStoredTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "light" || stored === "dark") return stored;
  } catch {
    // localStorage unavailable (private browsing, etc.) — fall back below.
  }
  return systemTheme();
}

export function applyTheme(theme: Theme) {
  document.documentElement.setAttribute("data-theme", theme);
}

export function initTheme(): Theme {
  const theme = getStoredTheme();
  applyTheme(theme);
  return theme;
}

export function setStoredTheme(theme: Theme) {
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // Ignore — theme still applies for this session.
  }
  applyTheme(theme);
}

export function toggleStoredTheme(current: Theme): Theme {
  const next: Theme = current === "dark" ? "light" : "dark";
  setStoredTheme(next);
  return next;
}
