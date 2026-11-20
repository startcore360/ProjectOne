type RosterRow = { id: string; name: string; status: string };
type Props = { rows: RosterRow[]; onChange: (id: string, status: string) => void; readOnly?: boolean };
export function StatusToggleRoster({ rows, onChange, readOnly = false }: Props) {
  if (rows.length === 0) return <p>No roster entries are available.</p>;
  return <ul>{rows.map((row) => <li key={row.id}><span>{row.name}</span><select aria-label={`Status for ${row.name}`} value={row.status} disabled={readOnly} onChange={(event) => onChange(row.id, event.target.value)}><option value="PRESENT">Present</option><option value="ABSENT">Absent</option><option value="LATE">Late</option><option value="ON_APPROVED_LEAVE">On approved leave</option></select></li>)}</ul>;
}
