import type { ProfileResponse } from "../api/profileService";

export function initialsFor(name: string): string {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "?";
}

export function roleLabel(profile: ProfileResponse): string {
  const raw = profile.platformRole ?? profile.userType ?? "";
  if (!raw) return "No role yet";
  return raw
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
