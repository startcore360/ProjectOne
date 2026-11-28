import { useCallback, type ChangeEvent, type FocusEvent, type InputHTMLAttributes } from "react";
import { Icon } from "../../components/ui/Icon";
import { validateEmail, validateRequired } from "./formValidation";

export type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  id: string;
  hint?: string;
  error?: string;
  fieldName?: string;
  setFieldError?: (field: string, message: string) => void;
  clearFieldError?: (field: string) => void;
  validator?: (value: string) => string | null;
};

export default function TextField({
  label,
  id,
  name,
  type = "text",
  required,
  hint,
  error,
  fieldName,
  setFieldError,
  clearFieldError,
  validator,
  onChange,
  onBlur,
  "aria-describedby": callerDescribedBy,
  "aria-invalid": callerInvalid,
  ...rest
}: TextFieldProps) {
  const targetField = fieldName || name || id;
  const hasAsterisk = label.includes("*");
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [errorId, hintId, callerDescribedBy].filter(Boolean).join(" ") || undefined;

  const handleBlur = useCallback(
    (e: FocusEvent<HTMLInputElement>) => {
      const val = e.target.value;
      if (setFieldError) {
        let validationError: string | null = null;
        if (validator) {
          validationError = validator(val);
        } else if (type === "email") {
          validationError = validateEmail(val, required);
        } else if (required) {
          validationError = validateRequired(val, label);
        }

        if (validationError) {
          setFieldError(targetField, validationError);
        } else if (clearFieldError) {
          clearFieldError(targetField);
        }
      }
      onBlur?.(e);
    },
    [clearFieldError, label, onBlur, required, setFieldError, targetField, type, validator]
  );

  const handleChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      if (clearFieldError) {
        clearFieldError(targetField);
      }
      onChange?.(e);
    },
    [clearFieldError, onChange, targetField]
  );

  return (
    <div className="eds-field">
      <label htmlFor={id}>
        {label} {required && !hasAsterisk && <span>(Required)</span>}
      </label>
      <input
        id={id}
        name={name}
        type={type}
        required={required}
        onChange={handleChange}
        onBlur={handleBlur}
        {...rest}
        aria-describedby={describedBy}
        aria-invalid={Boolean(error) || Boolean(callerInvalid)}
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
