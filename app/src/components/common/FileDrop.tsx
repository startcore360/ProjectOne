import { useRef } from "react";
type Props = { label: string; onFile: (file: File | null) => void; error?: string; maxBytes?: number; accept?: string };
export function FileDrop({ label, onFile, error, maxBytes = 10_485_760, accept = ".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.jpg,.jpeg,.png,.txt" }: Props) {
  const input = useRef<HTMLInputElement>(null);
  const select = (file?: File) => {
    if (file && file.size > maxBytes) { onFile(null); return; }
    onFile(file ?? null);
  };
  return <div><label>{label}<input ref={input} type="file" accept={accept} onChange={(event) => select(event.target.files?.[0])} /></label><p>PDF, Office documents, JPG, PNG or TXT. Maximum 10 MiB.</p>{error && <p role="alert">{error}</p>}</div>;
}
