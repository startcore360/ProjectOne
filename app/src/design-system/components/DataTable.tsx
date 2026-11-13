import { Children, isValidElement, useMemo, useState, type ReactElement, type ReactNode } from "react";
import { Button } from "./Button";

type TableCellElement = ReactElement<{ children?: ReactNode; colSpan?: number; rowSpan?: number }>;
type TableRowElement = ReactElement<{ children?: ReactNode; "data-no-mobile-card"?: boolean }>;

export interface DataTableProps {
  headers: string[];
  children: ReactNode;
  empty?: boolean;
  emptyMessage?: string;
  className?: string;
  tableClassName?: string;
  ariaLabel?: string;
  mobileCards?: boolean;
  /** Enable client-side pagination. Set false for server-paginated tables. */
  pagination?: boolean;
  pageSize?: number;
  pageSizeOptions?: number[];
}

export function DataTable({
  headers, children, empty, emptyMessage = "No matching records.", className = "",
  tableClassName = "", ariaLabel, mobileCards = true, pagination = true,
  pageSize = 10, pageSizeOptions = [10, 25, 50],
}: DataTableProps) {
  const rows = Children.toArray(children).filter((child): child is TableRowElement => isValidElement(child) && child.type === "tr");
  const cardRows = rows.filter((row) => {
    if (row.props["data-no-mobile-card"]) return false;
    const cells = Children.toArray(row.props.children).filter(
      (cell): cell is TableCellElement => isValidElement(cell) && (cell.type === "td" || cell.type === "th"),
    );
    return !cells.some((cell) => Number(cell.props.colSpan ?? 1) > 1 || Number(cell.props.rowSpan ?? 1) > 1);
  });
  const [currentPage, setCurrentPage] = useState(1);
  const [selectedPageSize, setSelectedPageSize] = useState(pageSize);
  const pageCount = Math.max(1, Math.ceil(rows.length / selectedPageSize));
  const safePage = Math.min(currentPage, pageCount);
  const visibleRows = useMemo(
    () => rows.slice((safePage - 1) * selectedPageSize, safePage * selectedPageSize),
    [rows, safePage, selectedPageSize],
  );
  const visibleCards = visibleRows.filter((row) => cardRows.includes(row));
  const start = rows.length ? (safePage - 1) * selectedPageSize + 1 : 0;
  const end = Math.min(safePage * selectedPageSize, rows.length);
  const paginated = pagination && rows.length > selectedPageSize;

  return (
    <div className={`platform-table-wrap ${className} ${mobileCards && !empty && cardRows.length > 0 ? "platform-table-wrap--mobile-cards" : ""}`.trim()}>
      <div className="platform-table-scroll" role="region" aria-label={ariaLabel ?? `Scrollable data table: ${headers.join(", ")}`} tabIndex={0}>
        <table className={`platform-table ${tableClassName}`.trim()}>
          <thead><tr>{headers.map((h, index) => <th key={`${h}-${index}`} scope="col">{h}</th>)}</tr></thead>
          <tbody>{empty ? <tr><td colSpan={headers.length} className="platform-empty-cell">{emptyMessage}</td></tr> : visibleRows}</tbody>
        </table>
      </div>
      {mobileCards && !empty && cardRows.length > 0 && (
        <div className="platform-table-cards" role="list" aria-label={ariaLabel ?? "Records"}>
          {visibleCards.map((row, rowIndex) => {
            const cells = Children.toArray(row.props.children).filter((cell): cell is TableCellElement => isValidElement(cell) && (cell.type === "td" || cell.type === "th"));
            return <article className="platform-table-card" role="listitem" key={row.key ?? rowIndex}>
              {cells.map((cell, cellIndex) => {
                const header = headers[cellIndex] ?? `Field ${cellIndex + 1}`;
                const isAction = /^(actions?|action)$/i.test(header.trim());
                return <div className={isAction ? "platform-table-card__actions" : "platform-table-card__field"} key={cell.key ?? cellIndex}>
                  {!isAction && <span className="platform-table-card__label">{header}</span>}
                  <div className="platform-table-card__value">{cell.props.children}</div>
                </div>;
              })}
            </article>;
          })}
        </div>
      )}
      {paginated && <nav className="platform-table-pagination" aria-label="Table pagination">
        <span className="platform-table-pagination__range">Showing {start}–{end} of {rows.length}</span>
        <label className="platform-table-pagination__size">Rows per page
          <select value={selectedPageSize} onChange={(event) => { setSelectedPageSize(Number(event.target.value)); setCurrentPage(1); }}>
            {Array.from(new Set([...pageSizeOptions, selectedPageSize])).sort((a, b) => a - b).map((size) => <option key={size} value={size}>{size}</option>)}
          </select>
        </label>
        <div className="platform-table-pagination__actions">
          <Button size="sm" variant="secondary" disabled={safePage <= 1} onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}>Previous</Button>
          <span aria-live="polite">Page {safePage} of {pageCount}</span>
          <Button size="sm" variant="secondary" disabled={safePage >= pageCount} onClick={() => setCurrentPage((page) => Math.min(pageCount, page + 1))}>Next</Button>
        </div>
      </nav>}
    </div>
  );
}
export default DataTable;
