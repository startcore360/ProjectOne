import type { ReactNode } from "react";

export function greeting(date: Date = new Date()): string {
  const h = date.getHours();
  if (h < 12) return "Good morning";
  if (h < 17) return "Good afternoon";
  return "Good evening";
}

export function formatGreetingName(displayName?: string | null, maxLength = 24): string {
  if (!displayName || !displayName.trim()) return "";
  let name = displayName.trim();

  // If display name is an email address (common in dev/test/SSO), format the username part
  if (name.includes("@")) {
    const part = name.split("@")[0].replace(/[._-]+/g, " ").trim();
    name = part.replace(/\b\w/g, (char) => char.toUpperCase());
  }

  // Handle long names gracefully
  if (name.length > maxLength) {
    const parts = name.split(/\s+/);
    // If multiple words and first word is reasonable, use the first name
    if (parts.length > 1 && parts[0].length <= maxLength) {
      return parts[0];
    }
    return name.slice(0, maxLength - 1).trimEnd() + "…";
  }

  return name;
}

export function renderGreeting(displayName?: string | null, date: Date = new Date()): ReactNode {
  const greet = greeting(date);
  const name = formatGreetingName(displayName);
  if (!name) return `${greet}.`;
  return (
    <>
      <span>{greet}, </span>
      <span className="app-greeting__name">{name}</span>
      <span>.</span>
    </>
  );
}
