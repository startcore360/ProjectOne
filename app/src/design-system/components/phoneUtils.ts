export function validatePhoneNumber(value: string, required = false): string | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return required ? "Mobile number is required." : null;
  }
  const e164Regex = /^\+[1-9]\d{7,14}$/;
  if (!e164Regex.test(trimmed)) {
    return "Include the country code, for example +919876543210.";
  }
  return null;
}
