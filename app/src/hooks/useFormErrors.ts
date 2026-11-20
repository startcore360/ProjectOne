import { useCallback, useState } from "react";
import { getApiFieldErrors, getApiErrorMessage } from "../services/apiHelper";

/**
 * useFormErrors
 *
 * Manages per-field inline validation errors that come from the backend's
 * Spring @Valid failures ({ code: "VALIDATION_FAILED", fieldErrors: {...} })
 * as well as client-side pre-submit validation.
 *
 * Usage:
 *   const { fieldErrors, formError, handleApiError, setFieldError, clearFieldError, clearAll } = useFormErrors();
 *
 *   // In submit handler:
 *   try {
 *     await api.post(...);
 *   } catch (err) {
 *     handleApiError(err);   // sets fieldErrors AND formError automatically
 *   }
 *
 *   // Wire to fields:
 *   <TextField id="institutionCode" error={fieldErrors.institutionCode} onChange={...} />
 *
 *   // Clear a field error when the user corrects it:
 *   onChange={(e) => { setInstCode(e.target.value); clearFieldError("institutionCode"); }}
 */
export function useFormErrors() {
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  /** Set a single field error (client-side validation). */
  const setFieldError = useCallback((field: string, message: string) => {
    setFieldErrors((prev) => ({ ...prev, [field]: message }));
  }, []);

  /** Clear a single field error – call this inside the field's onChange so the red text disappears the moment the user starts correcting. */
  const clearFieldError = useCallback((field: string) => {
    setFieldErrors((prev) => {
      if (!prev[field]) return prev; // already clean – avoid re-render
      const next = { ...prev };
      delete next[field];
      return next;
    });
  }, []);

  /** Clear all field errors and the top-level form error. */
  const clearAll = useCallback(() => {
    setFieldErrors({});
    setFormError(null);
  }, []);

  /**
   * Handle any error from an API call.
   * - If the backend returned per-field errors (VALIDATION_FAILED), populates fieldErrors.
   * - Always populates formError with the top-level human-readable message.
   */
  const handleApiError = useCallback((err: unknown) => {
    const fields = getApiFieldErrors(err);
    const hasFieldErrors = Object.keys(fields).length > 0;
    setFieldErrors(fields);
    // Only show a generic top-level error when there are no field-level errors to highlight
    setFormError(hasFieldErrors ? null : getApiErrorMessage(err));
  }, []);

  return {
    fieldErrors,
    formError,
    setFieldErrors,
    setFieldError,
    clearFieldError,
    clearAll,
    handleApiError,
    /** Convenience: set a top-level (non-field) form error without touching fieldErrors. */
    setFormError,
  };
}
