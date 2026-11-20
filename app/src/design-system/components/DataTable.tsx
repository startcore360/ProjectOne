import { Children, isValidElement, useState, useSyncExternalStore, type ReactElement, type ReactNode } from "react";
import Button from "./Button";
import { SkeletonText } from "./Skeleton";

type TableCellElement = ReactElement<{ children?: ReactNode; colSpan?: number; rowSpan?: number }>;
type TableRowElement = ReactElement<{ children?: ReactNode; "data-no-mobile-card"?: boolean }>;

export interface DataTableProps {
  headers: string[];
  children?: ReactNode;
  empty?: boolean;
  emptyMessage?: string;
  className?: string;
  tableClassName?: string;
  ariaLabel?: string;
  loading?: boolean;
  loadingLabel?: string;
  loadingRows?: number;
  mobileCards?: boolean;
  /** Enable client-side pagination. Set false for server-paginated tables. */
  pagination?: boolean;
  pageSize?: number;
  pageSizeOptions?: number[];
}

function useMobileQuery() {
  const mq = typeof window !== "undefined" ? window.matchMedia("(max-width: 640px)") : null;
  return useSyncExternalStore(
    (cb) => { mq?.addEventListener("change", cb); return () => mq?.removeEventListener("change", cb); },
    () => mq?.matches ?? false,
    () => false,
  );
}

export function DataTable({
  headers, children, empty, emptyMessage = "No matching records.", className = "",
  tableClassName = "", ariaLabel, loading = false, loadingLabel = "Loading data", loadingRows = 5,
  mobileCards = true, pagination = true,
  pageSize = 10, pageSizeOptions = [10, 25, 50],
}: DataTableProps) {
  const isMobile = useMobileQuery();
  const [currentPage, setCurrentPage] = useState(1);
  const [selectedPageSize, setSelectedPageSize] = useState(pageSize);
  if (loading) {
    const rowCount = Math.max(1, loadingRows);
    const loadingTable = (
      <div className="app-table-scroll" aria-hidden="true">
        <table className={`app-table ${tableClassName}`.trim()}>
          <thead>
            <tr>{headers.map((header, index) => (
              <th key={`${header}-${index}`} scope="col"><SkeletonText height="1rem" width="75%" /></th>
            ))}</tr>
          </thead>
          <tbody>
            {Array.from({ length: rowCount }, (_, rowIndex) => (
              <tr key={rowIndex}>
                {headers.map((_, colIndex) => (
                  <td key={colIndex}>
                    <SkeletonText height="1rem" width={colIndex === 0 ? "85%" : colIndex === headers.length - 1 ? "50%" : "70%"} />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );

    return (
      <div
        className={`app-table-wrap eds-skeleton-table-wrap ${className}`.trim()}
        role="status"
        aria-busy="true"
        aria-label={loadingLabel}
      >
        <span className="sr-only">{loadingLabel}</span>
        {loadingTable}
        {mobileCards && isMobile && (
          <div className="app-table-cards" aria-hidden="true">
            {Array.from({ length: rowCount }, (_, rowIndex) => (
              <article className="app-table-card" key={rowIndex}>
                {headers.map((header, colIndex) => {
                  const isAction = /^(actions?|action)$/i.test(header.trim());
                  return (
                    <div className={isAction ? "app-table-card__actions" : "app-table-card__field"} key={`${header}-${colIndex}`}>
                      {!isAction && <span className="app-table-card__label">{header}</span>}
                      <div className="app-table-card__value"><SkeletonText height="1rem" width="70%" /></div>
                    </div>
                  );
                })}
              </article>
            ))}
          </div>
        )}
      </div>
    );
  }

  const rows = Children.toArray(children).filter((child): child is TableRowElement => isValidElement(child) && child.type === "tr");
  const cardRows = rows.filter((row) => {
    if (row.props["data-no-mobile-card"]) return false;
    const cells = Children.toArray(row.props.children).filter(
      (cell): cell is TableCellElement => isValidElement(cell) && (cell.type === "td" || cell.type === "th"),
    );
    return !cells.some((cell) => Number(cell.props.colSpan ?? 1) > 1 || Number(cell.props.rowSpan ?? 1) > 1);
  });
  const pageCount = Math.max(1, Math.ceil(rows.length / selectedPageSize));
  const safePage = Math.min(currentPage, pageCount);
  // When pagination is enabled, slice the page; when pagination is false, render all rows.
  const visibleRows = pagination
    ? rows.slice((safePage - 1) * selectedPageSize, safePage * selectedPageSize)
    : rows;
  const visibleCards = visibleRows.filter((row) => cardRows.includes(row));
  const start = rows.length ? (safePage - 1) * selectedPageSize + 1 : 0;
  const end = Math.min(safePage * selectedPageSize, rows.length);
  const paginated = pagination && rows.length > selectedPageSize;

  return (
    <div className={`app-table-wrap ${className} ${mobileCards && !empty && cardRows.length > 0 ? "app-table-wrap--mobile-cards" : ""}`.trim()}>
      <div
        className="app-table-scroll"
        role="region"
        aria-label={ariaLabel ?? `Scrollable data table: ${headers.join(", ")}`}
        tabIndex={isMobile && mobileCards && !empty && cardRows.length > 0 ? -1 : 0}
        aria-hidden={isMobile && mobileCards && !empty && cardRows.length > 0 ? "true" : undefined}
      >
        <table className={`app-table ${tableClassName}`.trim()}>
          <thead><tr>{headers.map((h, index) => <th key={`${h}-${index}`} scope="col">{h}</th>)}</tr></thead>
          <tbody>{empty ? <tr><td colSpan={headers.length} className="app-empty-cell">{emptyMessage}</td></tr> : visibleRows}</tbody>
        </table>
      </div>
      {mobileCards && isMobile && !empty && cardRows.length > 0 && (
        <div className="app-table-cards" role="list" aria-label={ariaLabel ?? `Data cards: ${headers.join(", ")}`}>
          {visibleCards.map((row, rowIndex) => {
            const cells = Children.toArray(row.props.children).filter((cell): cell is TableCellElement => isValidElement(cell) && (cell.type === "td" || cell.type === "th"));
            return <article className="app-table-card" role="listitem" key={row.key ?? rowIndex}>
              {cells.map((cell, cellIndex) => {
                const header = headers[cellIndex] ?? `Field ${cellIndex + 1}`;
                const isAction = /^(actions?|action)$/i.test(header.trim());
                return <div className={isAction ? "app-table-card__actions" : "app-table-card__field"} key={cell.key ?? cellIndex}>
                  {!isAction && <span className="app-table-card__label">{header}</span>}
                  <div className="app-table-card__value">{cell.props.children}</div>
                </div>;
              })}
            </article>;
          })}
        </div>
      )}
      {paginated && <nav className="app-pagination" aria-label="Table pagination">
        <span className="app-pagination__range">{isMobile ? `${start}–${end} of ${rows.length}` : `Showing ${start}–${end} of ${rows.length}`}</span>
        <label className="app-pagination__size">{isMobile ? "Rows" : "Rows per page"}
          <select aria-label="Rows per page" value={selectedPageSize} onChange={(event) => { setSelectedPageSize(Number(event.target.value)); setCurrentPage(1); }}>
            {Array.from(new Set([...pageSizeOptions, selectedPageSize])).sort((a, b) => a - b).map((size) => <option key={size} value={size}>{size}</option>)}
          </select>
        </label>
        {isMobile ? (
          <div className="app-pagination__mobile-actions">
            <Button size="sm" variant="tertiary" aria-label="Previous page" disabled={safePage <= 1} onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}>‹</Button>
            <span className="app-pagination__page" aria-live="polite" aria-label={`Page ${safePage} of ${pageCount}`}>{safePage} / {pageCount}</span>
            <Button size="sm" variant="tertiary" aria-label="Next page" disabled={safePage >= pageCount} onClick={() => setCurrentPage((page) => Math.min(pageCount, page + 1))}>›</Button>
          </div>
        ) : (
          <div className="app-pagination__actions">
            <Button size="sm" variant="secondary" disabled={safePage <= 1} onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}>Previous</Button>
            <span aria-live="polite">Page {safePage} of {pageCount}</span>
            <Button size="sm" variant="secondary" disabled={safePage >= pageCount} onClick={() => setCurrentPage((page) => Math.min(pageCount, page + 1))}>Next</Button>
          </div>
        )}
      </nav>}
    </div>
  );
}
export default DataTable;
