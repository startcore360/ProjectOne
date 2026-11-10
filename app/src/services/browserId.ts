// SES-01 (multi-device sessions): a random id for THIS browser, sent as X-Browser-Id when signing
// in. The server keeps one active session per browser, so signing in again here replaces this
// browser's previous session while other devices stay signed in. The server stores only a hash.
//
// localStorage, not sessionStorage: it must be the same id across tabs and restarts of the same
// browser. It identifies nothing about the person - it is not a credential, and clearing it just
// makes this browser look new (worst case: one extra session row until it expires).
const BROWSER_ID_KEY = "edu360.browserId";

function newId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  // Older browsers: 128 random bits, hex.
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

export function getBrowserId(): string | null {
  try {
    let id = localStorage.getItem(BROWSER_ID_KEY);
    if (!id) {
      id = newId();
      localStorage.setItem(BROWSER_ID_KEY, id);
    }
    return id;
  } catch {
    // Storage blocked (private mode, policy): sign in still works, just without same-browser matching.
    return null;
  }
}
