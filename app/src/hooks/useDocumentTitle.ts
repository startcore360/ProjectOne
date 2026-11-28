import { useEffect } from "react";

const APP_TITLE_SUFFIX = "EDU 360";

/**
 * Sets document.title on mount or when title changes,
 * and reverts it to the previous title on unmount.
 *
 * @param title Specific page title, e.g. "Tenants" -> "Tenants — EDU 360". If empty or undefined, sets "EDU 360".
 */
export function useDocumentTitle(title?: string): void {
  useEffect(() => {
    const previousTitle = document.title;
    const nextTitle = title ? `${title} — ${APP_TITLE_SUFFIX}` : APP_TITLE_SUFFIX;
    document.title = nextTitle;

    return () => {
      document.title = previousTitle;
    };
  }, [title]);
}
