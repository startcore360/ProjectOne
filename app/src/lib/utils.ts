import { clsx, type ClassValue } from "clsx";

// A small helper for combining CSS class names, e.g. cn("btn", isActive && "btn--active").
export function cn(...inputs: ClassValue[]) {
  return clsx(inputs);
}
