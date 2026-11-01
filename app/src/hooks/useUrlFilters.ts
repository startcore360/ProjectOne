import { useMemo } from "react";

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
