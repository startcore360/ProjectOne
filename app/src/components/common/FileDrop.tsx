import { useId, useRef, useState } from "react";

type Props = {
  label: string;
  onFile: (file: File | null) => void;
  error?: string;
  maxBytes?: number;
  accept?: string;
};

const DEFAULT_MAX_BYTES = 10_485_760;
const DEFAULT_ACCEPT = ".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.jpg,.jpeg,.png,.txt";

function formatMiB(bytes: number) {
  if (bytes < 104_858) return `${bytes.toLocaleString()} bytes`;
  const value = bytes / (1024 * 1024);
  return `${Number.isInteger(value) ? value : value.toFixed(1)} MiB`;
}

export function FileDrop({
  label,
  onFile,
  error,
  maxBytes = DEFAULT_MAX_BYTES,
  accept = DEFAULT_ACCEPT,
}: Props) {
  const input = useRef<HTMLInputElement>(null);
  const id = useId();
  const [validationError, setValidationError] = useState<string | null>(null);
  const helpId = `${id}-help`;
  const errorId = `${id}-error`;
  const visibleError = error ?? validationError;

  const select = (file?: File) => {
    if (file && file.size > maxBytes) {
      setValidationError(`The selected file is too large. Choose a file no larger than ${formatMiB(maxBytes)}.`);
      onFile(null);
      if (input.current) input.current.value = "";
      return;
    }
    setValidationError(null);
    onFile(file ?? null);
  };

  return (
    <div className="file-drop">
      <label htmlFor={id}>{label}</label>
      <input
        ref={input}
        id={id}
        type="file"
        accept={accept}
        aria-invalid={Boolean(visibleError)}
        aria-describedby={visibleError ? `${helpId} ${errorId}` : helpId}
        onChange={(event) => select(event.target.files?.[0])}
      />
      <p id={helpId}>PDF, Office documents, JPG, PNG or TXT. Maximum {formatMiB(maxBytes)}.</p>
      {visibleError && <p id={errorId} role="alert">{visibleError}</p>}
    </div>
  );
}
