type Props = { message: string; onDismiss?: () => void };
export default function ClashWarningToast({ message, onDismiss }: Props) {
  if (!message) return null;
  return <div role="alert" className="notice notice-error" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
    <span>{message}</span>
    {onDismiss && <button type="button" onClick={onDismiss} aria-label="Dismiss clash warning">Dismiss</button>}
  </div>;
}