import type { TextareaHTMLAttributes } from "react";

export type TextAreaFieldProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string;
  id: string;
  hint?: string;
  error?: string;
};

export default function TextAreaField({
  label,
  id,
  required,
  hint,
  error,
  rows = 3,
  ...rest
}: TextAreaFieldProps) {
  const hasAsterisk = label.includes("*");
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [errorId, hintId, rest["aria-describedby"]].filter(Boolean).join(" ") || undefined;

  return (
    <div className="eds-field">
      <label htmlFor={id}>
        {label} {required && !hasAsterisk && <span>(Required)</span>}
      </label>
      <textarea
        id={id}
        required={required}
        rows={rows}
        aria-describedby={describedBy}
        aria-invalid={Boolean(error) || rest["aria-invalid"]}
        {...rest}
      />
      {hint && (
        <p id={hintId} className="eds-field__hint">
          <span aria-hidden="true">ⓘ</span> {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="eds-field__error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
