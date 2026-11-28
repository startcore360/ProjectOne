/**
 * Local calendar date helpers to avoid UTC day rollover bugs (EDU-UX-010, EDU-UX-030).
 * Standard new Date().toISOString().slice(0, 10) converts to UTC first, which can lag
 * behind the local calendar date for time zones like Asia/Kolkata (UTC+05:30) around midnight.
 */

export function getLocalDateString(date: Date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function getLocalMonthStart(date: Date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  return `${year}-${month}-01`;
}
