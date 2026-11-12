import type { InputHTMLAttributes, ReactNode } from "react";

export type CheckboxFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & {
  label: ReactNode;
  id: string;
  hint?: string;
};

export default function CheckboxField({ label, id, hint, className = "", ...rest }: CheckboxFieldProps) {
  const hintId = hint ? `${id}-hint` : undefined;

  return (
    <div className={`eds-field--checkbox ${className}`.trim()}>
      <input
        id={id}
        type="checkbox"
        aria-describedby={hintId ?? rest["aria-describedby"]}
        {...rest}
      />
      <label htmlFor={id}>
        <span>{label}</span>
        {hint && (
          <span id={hintId} className="eds-field__hint">
            {hint}
          </span>
        )}
      </label>
    </div>
  );
}
