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
  className?: string;
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
  className = "",
}: MetricCardProps) {
  const iconName = icon ?? mark ?? "clock";
  const contextText = context ?? note ?? "";

  return (
    <div className={`kpi ${className}`.trim()}>
      <div className="kpi__head">
        <p className="kpi__label">{label}</p>
        <span className="kpi__icon">
          <Icon name={iconName} size={16} />
        </span>
      </div>
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
      {contextText && <p className="kpi__context">{contextText}</p>}
      {progress && (
        <progress
          className="progress progress--thin"
          max={progress[1]}
          value={progress[0]}
          aria-label={progress[2]}
        />
      )}
    </div>
  );
}

export const Metric = MetricCard;
export const Kpi = MetricCard;
export default MetricCard;
