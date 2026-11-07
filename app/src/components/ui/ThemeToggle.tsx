import { useState } from "react";
import { getStoredTheme, toggleStoredTheme } from "../../lib/theme";
import { Icon } from "./Icon";

// The button that switches the whole app between light and dark mode.
// "fab" (used on signed-out pages, which have no topbar) floats bottom-right.
// "inline" (used inside AppLayout's topbar) sits as a plain icon button -
// a floating button was competing with the mobile bottom nav for the same
// corner, so the signed-in app no longer uses the fab variant at all.
export function ThemeToggle({ variant = "fab" }: { variant?: "fab" | "inline" }) {
  // Starts with whatever theme is already saved/active, so the icon matches
  // the page on first render instead of flashing the wrong one.
  const [theme, setTheme] = useState(getStoredTheme);

  return (
    <button
      type="button"
      className={"theme-toggle" + (variant === "fab" ? " theme-toggle--fab" : "")}
      aria-label={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}
      onClick={() => setTheme(toggleStoredTheme(theme))}
    >
      <Icon name={theme === "dark" ? "sun" : "moon"} size={18} />
    </button>
  );
}
