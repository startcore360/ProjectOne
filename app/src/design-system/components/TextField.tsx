import type { InputHTMLAttributes } from "react";
// The one place that renders the .eds-field pattern from components.css: a label (with a
// "Required" hint), an input, and the surrounding spacing - used by every text/email field in
// the app instead of hand-writing the same three elements per page.
export type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  id: string;
};

export default function TextField({ label, id, required, ...rest }: TextFieldProps) {
  return (
    <div className="eds-field">
      <label htmlFor={id}>
        {label} {required && <span>Required</span>}
      </label>
      <input id={id} required={required} {...rest} />
    </div>
  );
}
