import type { InputHTMLAttributes } from "react";
import { Icon } from "../../components/ui/Icon";
// The one place that renders the .eds-field pattern from components.css: a label (with a
// "Required" hint), an input, optional hint/error messages, and the surrounding spacing -
// used by every text/email/date field in the app instead of hand-writing elements per page.
export type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  id: string;
  hint?: string;
  error?: string;
};

export default function TextField({ label, id, required, hint, error, ...rest }: TextFieldProps) {
  const hasAsterisk = label.includes("*");
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [errorId, hintId, rest["aria-describedby"]].filter(Boolean).join(" ") || undefined;

  return (
    <div className="eds-field">
      <label htmlFor={id}>
        {label} {required && !hasAsterisk && <span>(Required)</span>}
      </label>
      <input
        id={id}
        required={required}
        aria-describedby={describedBy}
        aria-invalid={Boolean(error) || rest["aria-invalid"]}
        {...rest}
      />
      {hint && (
        <p id={hintId} className="eds-field__hint">
          <Icon name="info" size={14} /> {hint}
        </p>
      )}
      {error ? (
        <p id={errorId} className="eds-field__error" role="alert">{error}</p>
      ) : (
        <p className="eds-field__error-placeholder" aria-hidden="true">&nbsp;</p>
      )}
    </div>
  );
}
