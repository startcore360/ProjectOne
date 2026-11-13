import { Children, isValidElement, type ReactElement, type ReactNode } from "react";
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
}

export function DataTable({
  headers,
  children,
  empty,
  emptyMessage = "No matching records.",
  className = "",
  tableClassName = "",
  ariaLabel,
  mobileCards = true,
}: DataTableProps) {
  const rows = Children.toArray(children).filter((child): child is TableRowElement => isValidElement(child) && child.type === "tr");
  const cardRows = rows.filter((row) => {
    if (row.props["data-no-mobile-card"]) return false;
    const cells = Children.toArray(row.props.children).filter(
      (cell): cell is TableCellElement => isValidElement(cell) && (cell.type === "td" || cell.type === "th"),
    );
    return !cells.some((cell) => Number(cell.props.colSpan ?? 1) > 1 || Number(cell.props.rowSpan ?? 1) > 1);
  });

  return (
    <div className={`platform-table-wrap ${className}`.trim()}>
      <div
        className="platform-table-scroll"
        role="region"
        aria-label={ariaLabel ?? `Scrollable data table: ${headers.join(", ")}`}
        tabIndex={0}
      >
        <table className={`platform-table ${tableClassName}`.trim()}>
          <thead>
            <tr>
              {headers.map((h, index) => (
                <th key={`${h}-${index}`} scope="col">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {empty ? (
              <tr>
                <td colSpan={headers.length} className="platform-empty-cell">{emptyMessage}</td>
              </tr>
            ) : children}
          </tbody>
        </table>
      </div>
      {mobileCards && !empty && cardRows.length > 0 && (
        <div className="platform-table-cards" role="list" aria-label={ariaLabel ?? "Records"}>
          {cardRows.map((row, rowIndex) => {
            const cells = Children.toArray(row.props.children).filter((cell): cell is TableCellElement => isValidElement(cell) && (cell.type === "td" || cell.type === "th"));
            if (cells.some((cell) => Number(cell.props.colSpan ?? 1) > 1 || Number(cell.props.rowSpan ?? 1) > 1)) {
              return null;
            }
            return (
              <article className="platform-table-card" role="listitem" key={row.key ?? rowIndex}>
                {cells.map((cell, cellIndex) => {
                  const header = headers[cellIndex] ?? `Field ${cellIndex + 1}`;
                  const isAction = /^(actions?|action)$/i.test(header.trim());
                  return (
                    <div className={isAction ? "platform-table-card__actions" : "platform-table-card__field"} key={cell.key ?? cellIndex}>
                      {!isAction && <span className="platform-table-card__label">{header}</span>}
                      <div className="platform-table-card__value">{cell.props.children}</div>
                    </div>
                  );
                })}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default DataTable;
