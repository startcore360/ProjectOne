import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

// A small helper for combining CSS class names, e.g. cn("btn", isActive && "btn--active").
// It also removes conflicting Tailwind classes (like two different "padding"
// classes) so the last one wins instead of both applying at once.
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
