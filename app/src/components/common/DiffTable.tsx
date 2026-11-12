type DiffRow = { field: string; before: string; after: string };
export function DiffTable({ rows }: { rows: DiffRow[] }) {
  if (rows.length === 0) return <p>No changes to compare.</p>;
  return <table><thead><tr><th>Field</th><th>Previous</th><th>New</th></tr></thead><tbody>{rows.map((row) => <tr key={row.field}><th scope="row">{row.field}</th><td>{row.before}</td><td>{row.after}</td></tr>)}</tbody></table>;
}
