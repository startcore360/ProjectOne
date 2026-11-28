import { useCallback, type ChangeEvent, type FocusEvent, type InputHTMLAttributes } from "react";
import { Icon } from "../../components/ui/Icon";
import { validatePhoneNumber } from "./phoneUtils";

export interface PhoneFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  id: string;
  name?: string;
  label?: string;
  hint?: string;
  error?: string;
  required?: boolean;
  fieldName?: string;
  setFieldError?: (field: string, message: string) => void;
  clearFieldError?: (field: string) => void;
}

export default function PhoneField({
  id,
  name,
  label = "Mobile number",
  hint = "Include the country code, for example +919876543210.",
  placeholder = "+919876543210",
  error,
  required,
  fieldName,
  setFieldError,
  clearFieldError,
  value,
  onChange,
  onBlur,
  "aria-describedby": callerDescribedBy,
  "aria-invalid": callerInvalid,
  ...rest
}: PhoneFieldProps) {
  const targetField = fieldName || name || id;
  const hasAsterisk = label.includes("*");
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [errorId, hintId, callerDescribedBy].filter(Boolean).join(" ") || undefined;

  const handleBlur = useCallback(
    (e: FocusEvent<HTMLInputElement>) => {
      const val = e.target.value;
      if (setFieldError) {
        const validationError = validatePhoneNumber(val, required);
        if (validationError) {
          setFieldError(targetField, validationError);
        } else if (clearFieldError) {
          clearFieldError(targetField);
        }
      }
      onBlur?.(e);
    },
    [clearFieldError, onBlur, required, setFieldError, targetField]
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
        type="tel"
        inputMode="tel"
        placeholder={placeholder}
        required={required}
        value={value}
        onChange={handleChange}
        onBlur={handleBlur}
        aria-describedby={describedBy}
        aria-invalid={Boolean(error) || Boolean(callerInvalid)}
        {...rest}
      />
      {hint && (
        <p id={hintId} className="eds-field__hint">
          <Icon name="info" size={14} /> {hint}
        </p>
      )}
      {error ? (
        <p id={errorId} className="eds-field__error" role="alert">
          {error}
        </p>
      ) : (
        <p className="eds-field__error-placeholder" aria-hidden="true">
          &nbsp;
        </p>
      )}
    </div>
  );
}
