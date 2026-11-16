type Option = { id: string; label: string };
type Props = { value: string; options: Option[]; onChange: (id: string) => void; disabled?: boolean };
export function SectionPicker({ value, options, onChange, disabled = false }: Props) {
  return (
    <label className="field-label">
      Section
      <select className="app-input" value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)}>
        <option value="">Select a section</option>
        {options.map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}
      </select>
    </label>
  );
}
