import type { CSSProperties, ReactNode } from "react";
import "./Skeleton.css";

export interface SkeletonProps {
  className?: string;
  style?: CSSProperties;
  width?: string | number;
  height?: string | number;
  borderRadius?: string;
}

export function SkeletonText({
  lines = 1,
  width,
  height = "1em",
  className = "",
  style,
}: SkeletonProps & { lines?: number }) {
  if (lines === 1) {
    return (
      <span
        className={`eds-skeleton eds-skeleton--text ${className}`.trim()}
        style={{ width, height, ...style }}
        aria-hidden="true"
      />
    );
  }

  return (
    <div className={`eds-skeleton-group ${className}`.trim()} style={style} aria-hidden="true">
      {Array.from({ length: lines }).map((_, index) => (
        <span
          key={index}
          className="eds-skeleton eds-skeleton--text"
          style={{
            height,
            width: index === lines - 1 && lines > 1 ? "60%" : width ?? "100%",
          }}
        />
      ))}
    </div>
  );
}

export function SkeletonAvatar({
  size = 40,
  className = "",
  style,
}: SkeletonProps & { size?: number }) {
  return (
    <span
      className={`eds-skeleton eds-skeleton--avatar ${className}`.trim()}
      style={{ width: size, height: size, ...style }}
      aria-hidden="true"
    />
  );
}

export function SkeletonCard({
  className = "",
  style,
  children,
}: {
  className?: string;
  style?: CSSProperties;
  children?: ReactNode;
}) {
  return (
    <div className={`eds-skeleton-card ${className}`.trim()} style={style} aria-hidden="true">
      {children ?? (
        <>
          <SkeletonText height="1.25rem" width="40%" />
          <SkeletonText lines={2} height="0.875rem" />
        </>
      )}
    </div>
  );
}

export function SkeletonMetricCard({ className = "" }: { className?: string }) {
  return (
    <div className={`eds-skeleton-metric-card ${className}`.trim()} aria-hidden="true">
      <SkeletonText height="0.75rem" width="50%" />
      <SkeletonText height="2rem" width="70%" style={{ margin: "var(--space-2) 0" }} />
      <SkeletonText height="0.75rem" width="40%" />
    </div>
  );
}

export function SkeletonTable({
  rows = 5,
  columns = 4,
  className = "",
}: {
  rows?: number;
  columns?: number;
  className?: string;
}) {
  return (
    <div
      className={`app-table-wrap eds-skeleton-table-wrap ${className}`.trim()}
      role="status"
      aria-busy="true"
      aria-label="Loading table"
    >
      <span className="sr-only">Loading table...</span>
      <div className="app-table-scroll" aria-hidden="true">
        <table className="app-table">
          <thead>
            <tr>
              {Array.from({ length: columns }).map((_, i) => (
                <th key={i}>
                  <SkeletonText height="1rem" width="75%" />
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: rows }).map((_, rowIndex) => (
              <tr key={rowIndex}>
                {Array.from({ length: columns }).map((_, colIndex) => (
                  <td key={colIndex}>
                    <SkeletonText
                      height="1rem"
                      width={colIndex === 0 ? "85%" : colIndex === columns - 1 ? "50%" : "70%"}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function SkeletonPage({
  title = "Loading page",
  showKpis = false,
  kpiCount = 4,
  showTable = true,
  tableRows = 5,
  tableColumns = 5,
}: {
  title?: string;
  showKpis?: boolean;
  kpiCount?: number;
  showTable?: boolean;
  tableRows?: number;
  tableColumns?: number;
}) {
  return (
    <div className="app-page eds-skeleton-page" role="status" aria-busy="true" aria-label={title}>
      <span className="sr-only">{title}</span>
      <div className="app-page-header" aria-hidden="true" style={{ marginBottom: "var(--space-5)" }}>
        <SkeletonText height="2rem" width="280px" style={{ marginBottom: "var(--space-2)" }} />
        <SkeletonText height="1rem" width="460px" />
      </div>

      {showKpis && (
        <div className="kpi-grid" aria-hidden="true" style={{ marginBottom: "var(--space-5)" }}>
          {Array.from({ length: kpiCount }).map((_, index) => (
            <SkeletonMetricCard key={index} />
          ))}
        </div>
      )}

      {showTable && (
        <div className="app-panel" aria-hidden="true">
          <div style={{ padding: "var(--space-4) var(--space-5)" }}>
            <SkeletonText height="1.25rem" width="220px" style={{ marginBottom: "var(--space-2)" }} />
            <SkeletonText height="0.875rem" width="380px" />
          </div>
          <SkeletonTable rows={tableRows} columns={tableColumns} />
        </div>
      )}
    </div>
  );
}
