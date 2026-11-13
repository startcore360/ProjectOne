import type { ReactNode } from "react";

export interface DataTableProps {
  headers: string[];
  children: ReactNode;
  empty?: boolean;
  emptyMessage?: string;
  className?: string;
  tableClassName?: string;
  ariaLabel?: string;
}

export function DataTable({
  headers,
  children,
  empty,
  emptyMessage = "No matching records.",
  className = "",
  tableClassName = "",
  ariaLabel,
}: DataTableProps) {
  return (
    <div
      className={`platform-table-wrap ${className}`.trim()}
      role="region"
      aria-label={ariaLabel ?? `Scrollable data table: ${headers.join(", ")}`}
      tabIndex={0}
    >
      <table className={`platform-table ${tableClassName}`.trim()}>
        <thead>
          <tr>
            {headers.map((h) => (
              <th key={h} scope="col">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {empty ? (
            <tr>
              <td colSpan={headers.length} className="platform-empty-cell">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            children
          )}
        </tbody>
      </table>
    </div>
  );
}

export default DataTable;
