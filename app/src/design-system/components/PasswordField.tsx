import { useState, type InputHTMLAttributes } from "react";
// The one place that renders the .eds-field + .auth-password pattern: a labeled password
// input with a show/hide toggle. Used by every password field in the app (login, change
// password) instead of re-implementing the toggle state per page.
export type PasswordFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  id: string;
};

export default function PasswordField({ label, id, required, ...rest }: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);
  const hasAsterisk = label.includes("*");

  return (
    <div className="eds-field">
      <label htmlFor={id}>
        {label} {required && !hasAsterisk && <span>(Required)</span>}
      </label>
      <div className="auth-password">
        <input id={id} type={visible ? "text" : "password"} required={required} {...rest} />
        <button
          className="auth-password__toggle"
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-pressed={visible}
          aria-controls={id}
        >
          {visible ? "Hide" : "Show"}
        </button>
      </div>
    </div>
  );
}
