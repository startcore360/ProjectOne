import type { ReactNode, SelectHTMLAttributes } from "react";

export type SelectOption = {
  value: string | number;
  label: string;
  disabled?: boolean;
};

export type SelectFieldProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  id: string;
  hint?: string;
  error?: string;
  options?: SelectOption[];
  children?: ReactNode;
};

export default function SelectField({
  label,
  id,
  required,
  hint,
  error,
  options,
  children,
  ...rest
}: SelectFieldProps) {
  const hasAsterisk = label.includes("*");
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [errorId, hintId, rest["aria-describedby"]].filter(Boolean).join(" ") || undefined;

  return (
    <div className="eds-field">
      <label htmlFor={id}>
        {label} {required && !hasAsterisk && <span>(Required)</span>}
      </label>
      <select
        id={id}
        required={required}
        aria-describedby={describedBy}
        aria-invalid={Boolean(error) || rest["aria-invalid"]}
        {...rest}
      >
        {options
          ? options.map((opt) => (
              <option key={String(opt.value)} value={opt.value} disabled={opt.disabled}>
                {opt.label}
              </option>
            ))
          : children}
      </select>
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
