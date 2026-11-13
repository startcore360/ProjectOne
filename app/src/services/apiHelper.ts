import type { AxiosError } from "axios";

export type ApiErrorResponse = {
  message?: string;
  error?: string;
  details?: string;
  errors?: Record<string, string>;
  /** Spring @Valid field-level errors: { fieldName: "error message" } */
  fieldErrors?: Record<string, string>;
};

export type ApiError = AxiosError<ApiErrorResponse>;

// Small shared helpers for talking to the backend API: building URLs,
// turning a failed request into a friendly on-screen message, and turning
// a filter object into a "?key=value" query string.
/**
 * Builds an API path using the backend's current /api route prefix.
 * Axios service calls should continue using relative paths with the shared
 * Axios instance (whose baseURL already includes /api).
 */
function isInternalErrorText(value: string): boolean {
  const text = value.trim();
  return !text || /^[A-Z][A-Z0-9_]{2,}$/.test(text) ||
    /correlation\\s*id\\s*:/i.test(text) ||
    /^(?:org\\.springframework|java\\.|com\\.edu360\\.)/.test(text);
}

function cleanUserMessage(value: string | undefined): string | null {
  if (!value) return null;
  if (/does not hold action\\s+[A-Z0-9_]+/i.test(value) || /does not hold permission\\s+[A-Z0-9_]+/i.test(value)) {
    return "Your current role does not have permission to perform this action. Ask your administrator to review your access.";
  }
  const cleaned = value
    .replace(/\\s*correlation\\s*id\\s*:\\s*[a-f0-9-]{16,}/ig, "")
    .replace(/\\s*\\(?(?:code|error)\\s*:\\s*[A-Z][A-Z0-9_]{2,}\\)?/g, "")
    .trim();
  return isInternalErrorText(cleaned) ? null : cleaned;
}

export class ApiHelper {
  static getUrl(path: string): string {
    const normalizedPath = path.replace(/^\/+/, "");
    return `/api/${normalizedPath}`;
  }

  // Turns whatever error axios throws into one plain sentence to show the
  // user. It checks several possible places the backend might have put its
  // message, then falls back to a friendly generic message (network issue,
  // expired session, no permission, server error) so the UI never shows a
  // raw technical error.
  static normalizeError(error: unknown): string {
    if (typeof error === "string" && error.trim()) {
      return cleanUserMessage(error) ?? "Something went wrong. Please try again.";
    }

    if (error && typeof error === "object" && "isAxiosError" in error) {
      const apiError = error as ApiError;
      const status = apiError.response?.status;
      if (status === 401) return "Your session has expired. Please sign in again.";
      if (status === 403) return "You do not have permission to perform this action.";
      if (status === 404) return "The requested record could not be found.";
      if (status === 409 || status === 412) return "This record has changed. Refresh it and try again.";
      if (status && status >= 500) return "The server encountered an error. Please try again later.";

      const responseMessage = cleanUserMessage(
        apiError.response?.data?.message ||
        apiError.response?.data?.error ||
        apiError.response?.data?.details,
      );
      if (responseMessage) return responseMessage;
      if (apiError.code === "ERR_NETWORK") {
        return "Unable to reach the server. Check your connection and try again.";
      }

    }

    if (error instanceof Error && error.message) return cleanUserMessage(error.message) ?? "Something went wrong. Please try again.";
    return "Something went wrong. Please try again.";
  }

  static toQueryString(
    params: Record<string, string | number | boolean | null | undefined>,
  ): string {
    const query = new URLSearchParams();

    Object.entries(params).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== "") {
        query.set(key, String(value));
      }
    });

    const serialized = query.toString();
    return serialized ? `?${serialized}` : "";
  }
}

export const getApiErrorMessage = ApiHelper.normalizeError;

/**
 * Extracts per-field validation errors from a Spring @Valid failure response.
 * The backend sends: { code: "VALIDATION_FAILED", fieldErrors: { fieldName: "message" } }
 *
 * Returns an empty object when the error is not a validation failure, so callers
 * can safely spread the result into form state without additional null-checks.
 *
 * Usage:
 *   const fieldErrors = getApiFieldErrors(err);
 *   // fieldErrors.institutionCode => "must not be blank"
 */
export function getApiFieldErrors(error: unknown): Record<string, string> {
  if (error && typeof error === "object" && "isAxiosError" in error) {
    const apiError = error as ApiError;
    const data = apiError.response?.data;
    if (data?.fieldErrors && typeof data.fieldErrors === "object") {
      return data.fieldErrors as Record<string, string>;
    }
    // Also handle the older `errors` shape some endpoints return
    if (data?.errors && typeof data.errors === "object") {
      return data.errors as Record<string, string>;
    }
  }
  return {};
}
