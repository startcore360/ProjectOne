type Props = { label: string; value: string; onChange: (value: string) => void; min?: string; max?: string; disabled?: boolean };
export function DateField({ label, value, onChange, min, max, disabled = false }: Props) {
  return <label>{label}<input type="date" value={value} min={min} max={max} disabled={disabled} onChange={(event) => onChange(event.target.value)} /></label>;
}
