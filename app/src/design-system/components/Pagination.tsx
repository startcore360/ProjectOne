import type { CSSProperties } from "react";
import Button from "./Button";

export interface PaginationProps {
  /** Current active page (1-based index) */
  currentPage: number;
  /** Total number of pages */
  totalPages: number;
  /** Total number of items across all pages */
  totalItems?: number;
  /** Number of items per page */
  pageSize?: number;
  /** Number of items displayed on the current page */
  currentItemsCount?: number;
  /** Whether to show the rows per page dropdown (defaults to true when pageSizeOptions & onPageSizeChange are present) */
  showPageSize?: boolean;
  /** Options for the rows per page dropdown */
  pageSizeOptions?: number[];
  /** Callback fired when page changes (receives 1-based page number) */
  onPageChange: (page: number) => void;
  /** Callback fired when page size changes */
  onPageSizeChange?: (pageSize: number) => void;
  /** Accessible label for the pagination nav landmark */
  ariaLabel?: string;
  /** Disable navigation buttons (e.g. while loading) */
  disabled?: boolean;
  className?: string;
  style?: CSSProperties;
}

export function Pagination({
  currentPage,
  totalPages,
  totalItems,
  pageSize,
  currentItemsCount,
  showPageSize = true,
  pageSizeOptions,
  onPageChange,
  onPageSizeChange,
  ariaLabel = "Pagination",
  disabled = false,
  className = "",
  style,
}: PaginationProps) {
  const safeTotalPages = Math.max(1, totalPages || 1);
  const safePage = Math.min(Math.max(1, currentPage || 1), safeTotalPages);

  // Compute item range text if totalItems is provided
  let rangeText = "";
  if (typeof totalItems === "number" && totalItems >= 0) {
    if (totalItems === 0) {
      rangeText = "0 of 0";
    } else {
      const size = pageSize || currentItemsCount || 25;
      const count = currentItemsCount !== undefined ? currentItemsCount : Math.min(size, totalItems - (safePage - 1) * size);
      const start = (safePage - 1) * size + 1;
      const end = Math.min(totalItems, (safePage - 1) * size + count);
      rangeText = `Showing ${start}–${end} of ${totalItems}`;
    }
  }

  const hasPageSize = Boolean(showPageSize && pageSizeOptions && pageSizeOptions.length > 0 && onPageSizeChange);

  return (
    <nav
      className={`app-pagination ${className}`.trim()}
      aria-label={ariaLabel}
      style={style}
    >
      <div className="app-pagination__info">
        {rangeText ? (
          <span className="app-pagination__range">{rangeText}</span>
        ) : null}

        {hasPageSize ? (
          <>
            {rangeText ? (
              <span className="app-pagination__divider" aria-hidden="true">
                ·
              </span>
            ) : null}
            <label className="app-pagination__size">
              <span className="app-pagination__size-label-full">Rows per page</span>
              <span className="app-pagination__size-label-short">Rows</span>
              <select
                aria-label="Rows per page"
                value={pageSize}
                disabled={disabled}
                onChange={(e) => onPageSizeChange?.(Number(e.target.value))}
              >
                {Array.from(new Set([...(pageSizeOptions ?? []), ...(pageSize ? [pageSize] : [])]))
                  .sort((a, b) => a - b)
                  .map((size) => (
                    <option key={size} value={size}>
                      {size}
                    </option>
                  ))}
              </select>
            </label>
          </>
        ) : null}
      </div>

      <div className="app-pagination__actions">
        <Button
          variant="secondary"
          size="sm"
          disabled={disabled || safePage <= 1}
          onClick={() => onPageChange(Math.max(1, safePage - 1))}
        >
          Previous
        </Button>

        <span aria-live="polite">
          Page {safePage} of {safeTotalPages}
        </span>

        <Button
          variant="secondary"
          size="sm"
          disabled={disabled || safePage >= safeTotalPages}
          onClick={() => onPageChange(Math.min(safeTotalPages, safePage + 1))}
        >
          Next
        </Button>
      </div>
    </nav>
  );
}

export default Pagination;
