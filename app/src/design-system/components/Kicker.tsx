import type { ReactNode } from "react";
// The one place that renders the .eds-kicker class: the small uppercase label above a
// heading ("WELCOME BACK", "AT A GLANCE").
export default function Kicker({ children }: { children: ReactNode }) {
  return <p className="eds-kicker">{children}</p>;
}
