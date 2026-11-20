type Props = { title?: string; warnings: string[] };
export function WarningsPanel({ title = "Please review", warnings }: Props) {
  if (warnings.length === 0) return null;
  return <section aria-labelledby="warnings-title"><h2 id="warnings-title">{title}</h2><ul>{warnings.map((warning, index) => <li key={`${index}-${warning}`}>{warning}</li>)}</ul></section>;
}
