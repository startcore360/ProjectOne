export class ApiHelper {
  static getUrl(path: string) {
    return `/api/v1/${path.replace(/^\/+/, "")}`;
  }

  static normalizeError(error: any) {
    return (
      error?.response?.data?.message ||
      error?.message ||
      "Something went wrong."
    );
  }
}
