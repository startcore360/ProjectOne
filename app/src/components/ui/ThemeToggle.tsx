import { useState } from "react";
import { getStoredTheme, toggleStoredTheme } from "../../lib/theme";

// The floating button (bottom-right corner) that switches the whole app
// between light and dark mode. It's mounted once in App.tsx so it shows up
// on every screen, instead of being added to each page separately.
export function ThemeToggle() {
  // Starts with whatever theme is already saved/active, so the icon matches
  // the page on first render instead of flashing the wrong one.
  const [theme, setTheme] = useState(getStoredTheme);

  return (
    <button
      type="button"
      className="theme-toggle theme-toggle--fab"
      aria-label={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}
      onClick={() => setTheme(toggleStoredTheme(theme))}
    >
      {theme === "dark" ? "☀" : "☾"}
    </button>
  );
}
