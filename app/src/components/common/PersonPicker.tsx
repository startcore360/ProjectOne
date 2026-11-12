import type { ChangeEvent } from "react";

export type PersonOption = { id: string; name: string; designation?: string; department?: string };

type Props = { label: string; value: string; options: PersonOption[]; onChange: (id: string) => void; disabled?: boolean };

export function PersonPicker({ label, value, options, onChange, disabled = false }: Props) {
  return <label>{label}<select value={value} disabled={disabled} onChange={(event: ChangeEvent<HTMLSelectElement>) => onChange(event.target.value)}>
    <option value="">Select a person</option>
    {options.map((person) => <option key={person.id} value={person.id}>{person.name}{person.designation ? ` — ${person.designation}` : ""}{person.department ? ` · ${person.department}` : ""}</option>)}
  </select></label>;
}
