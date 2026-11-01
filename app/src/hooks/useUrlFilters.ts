import { useMemo } from "react";

// Reads filter values (search text, page number, status) straight out of the
// page's URL (like ?search=foo&page=2), so a filtered view can be bookmarked
// or shared as a link.
export function useUrlFilters() {
  return useMemo(() => {
    const params = new URLSearchParams(window.location.search);

    return {
      search: params.get("search") ?? "",
      page: Number(params.get("page") ?? "1"),
      status: params.get("status") ?? "",
    };
  }, []);
}
