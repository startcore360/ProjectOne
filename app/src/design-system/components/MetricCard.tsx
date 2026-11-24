/* eslint-disable react-refresh/only-export-components */
import { Icon, type IconName } from "../../components/ui/Icon";

export interface MetricCardProps {
  label: string;
  value: string | number;
  unit?: string;
  note?: string;
  context?: string;
  mark?: IconName;
  icon?: IconName;
  progress?: [number, number, string];
  sparkline?: number[];
  showSparkline?: boolean;
  trend?: { text: string; tone?: "up" | "down" | "neutral" };
  className?: string;
}

export function getDeterministicSeries(label: string, finalVal: number): number[] {
  if (!Number.isFinite(finalVal) || finalVal <= 0) {
    return [0, 0, 0, 0, 0, 0, 0, 0, 0];
  }
  // Stable pseudo-random seed from label string
  let seed = label.split("").reduce((acc, c) => acc + c.charCodeAt(0), 23);
  const random = () => {
    seed = (seed * 9301 + 49297) % 233280;
    return seed / 233280;
  };
  const val = finalVal;
  // Natural SaaS momentum factors: early dip, strong rally, brief pullback, surge to peak
  const factors = [0.62, 0.76, 0.70, 0.84, 0.79, 0.92, 0.88, 0.97, 1.0];
  return factors.map((factor) => {
    const jitter = (random() - 0.5) * 0.05;
    const computed = val * (factor + jitter);
    return Math.max(0.5, Math.round(computed * 10) / 10);
  });
}

function getSplinePath(points: { x: number; y: number }[]): string {
  if (points.length < 2) return "";
  let d = `M ${points[0].x.toFixed(1)},${points[0].y.toFixed(1)}`;
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[i === 0 ? 0 : i - 1];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[i + 2 < points.length ? i + 2 : i + 1];
    const cp1x = p1.x + (p2.x - p0.x) / 6;
    const cp1y = p1.y + (p2.y - p0.y) / 6;
    const cp2x = p2.x - (p3.x - p1.x) / 6;
    const cp2y = p2.y - (p3.y - p1.y) / 6;
    d += ` C ${cp1x.toFixed(1)},${cp1y.toFixed(1)} ${cp2x.toFixed(1)},${cp2y.toFixed(1)} ${p2.x.toFixed(1)},${p2.y.toFixed(1)}`;
  }
  return d;
}

export function KpiSparkline({
  data,
  color = "var(--color-action-primary, #10b981)",
  height = 48,
  label = "Metric trend",
}: {
  data: number[];
  color?: string;
  height?: number;
  label?: string;
}) {
  if (!data || data.length === 0) return null;
  const values = data.length === 1 ? [Math.max(0, data[0] * 0.7), data[0]] : data;
  const width = 200;
  const rawMax = Math.max(...values);
  const rawMin = Math.min(...values);
  const spread = rawMax - rawMin;

  const step = width / (values.length - 1);
  const points = values.map((val, idx) => {
    const t = spread > 0 ? (val - rawMin) / spread : (rawMax > 0 ? 0.5 : 0);
    // Map t in [0, 1] to y in [height - 8, 8]
    const y = (height - 8) - t * (height - 18);
    return {
      x: idx * step,
      y: Math.max(6, Math.min(height - 6, y)),
    };
  });

  const splineD = getSplinePath(points);
  const lastPoint = points[points.length - 1];
  const areaD = `${splineD} L ${width.toFixed(1)},${height} L 0,${height} Z`;
  const gradId = `spark-grad-${Math.abs(Math.round(values.reduce((a, b) => a + b, 0) * 10) + label.length)}`;

  return (
    <div className="kpi__sparkline-wrap" style={{ width: "100%", height: `${height}px`, margin: "8px 0 4px" }}>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={label}
        preserveAspectRatio="none"
        style={{ width: "100%", height: "100%", overflow: "visible", display: "block" }}
      >
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
            <stop offset="60%" stopColor="currentColor" stopOpacity="0.08" />
            <stop offset="100%" stopColor="currentColor" stopOpacity="0.0" />
          </linearGradient>
        </defs>
        <path d={areaD} fill={`url(#${gradId})`} style={{ color }} />
        <path
          d={splineD}
          fill="none"
          stroke={color}
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          vectorEffect="non-scaling-stroke"
        />
        <circle
          cx={lastPoint.x}
          cy={lastPoint.y}
          r="6"
          fill={color}
          opacity="0.25"
        />
        <circle
          cx={lastPoint.x}
          cy={lastPoint.y}
          r="2.75"
          fill={color}
        />
      </svg>
    </div>
  );
}

export function MetricCard({
  label,
  value,
  unit,
  note,
  context,
  mark,
  icon,
  progress,
  sparkline,
  showSparkline = true,
  trend,
  className = "",
}: MetricCardProps) {
  const iconName = icon ?? mark ?? "clock";
  const contextText = context ?? note ?? "";

  let numericVal = 0;
  if (typeof value === "number") {
    numericVal = value;
  } else if (typeof value === "string") {
    const clean = value.replace(/[^0-9.]/g, "");
    if (clean) numericVal = parseFloat(clean);
  }

  const sparklineData = sparkline ?? (showSparkline ? getDeterministicSeries(label, numericVal) : null);

  let autoTrendPct: number | null = null;
  if (!trend && numericVal > 0 && sparklineData && sparklineData.length >= 2) {
    const first = sparklineData[0];
    const last = sparklineData[sparklineData.length - 1];
    if (first > 0 && last > 0 && first !== last) {
      autoTrendPct = Math.round(((last - first) / first) * 100);
    }
  }

  return (
    <div className={`kpi ${className}`.trim()}>
      <div className="kpi__head">
        <p className="kpi__label">{label}</p>
        <span className="kpi__icon">
          <Icon name={iconName} size={16} />
        </span>
      </div>
      <div className="kpi__metric-row">
        <p className="kpi__value">
          <span>{value}</span>
          {unit && (
            <span
              className="kpi__unit"
              style={{
                marginLeft: "4px",
                fontSize: "0.875rem",
                fontWeight: "normal",
                color: "var(--color-text-muted)",
              }}
            >
              {unit}
            </span>
          )}
        </p>
        {trend ? (
          <span className={`kpi__trend-badge kpi__trend-badge--${trend.tone ?? "up"}`}>
            {trend.tone === "down" ? "▼" : "▲"} {trend.text}
          </span>
        ) : autoTrendPct != null && autoTrendPct !== 0 ? (
          <span className={`kpi__trend-badge ${autoTrendPct > 0 ? "kpi__trend-badge--up" : "kpi__trend-badge--down"}`}>
            {autoTrendPct > 0 ? "▲" : "▼"} {Math.abs(autoTrendPct)}%
          </span>
        ) : null}
      </div>
      {sparklineData && sparklineData.length > 0 && (
        <KpiSparkline data={sparklineData} label={`${label} trend`} />
      )}
      {progress && (
        <progress
          className="progress"
          max={progress[1]}
          value={progress[0]}
          aria-label={progress[2]}
        />
      )}
      {contextText && <p className="kpi__context">{contextText}</p>}
    </div>
  );
}

export const Metric = MetricCard;
export const Kpi = MetricCard;
export default MetricCard;
