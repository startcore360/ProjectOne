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
      return error;
    }

    if (error && typeof error === "object" && "isAxiosError" in error) {
      const apiError = error as ApiError;
      const responseMessage =
        apiError.response?.data?.message ||
        apiError.response?.data?.error ||
        apiError.response?.data?.details;

      if (responseMessage) return responseMessage;
      if (apiError.code === "ERR_NETWORK") {
        return "Unable to reach the server. Check your connection and try again.";
      }
      if (apiError.response?.status === 401) {
        return "Your session has expired. Please sign in again.";
      }
      if (apiError.response?.status === 403) {
        return "You do not have permission to perform this action.";
      }
      if (apiError.response?.status && apiError.response.status >= 500) {
        return "The server encountered an error. Please try again later.";
      }
    }

    if (error instanceof Error && error.message) return error.message;
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
