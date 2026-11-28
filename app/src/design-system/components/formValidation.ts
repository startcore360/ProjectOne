export function validateRequired(value: string, fieldLabel = "This field"): string | null {
  const trimmed = value.trim();
  if (!trimmed) {
    const cleanLabel = fieldLabel
      .replace(/\s*\*.*$/, "")
      .replace(/\s*\(Required\).*$/, "")
      .trim();
    return `${cleanLabel || "This field"} is required.`;
  }
  return null;
}

export function validateEmail(value: string, required = false): string | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return required ? "Email address is required." : null;
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(trimmed)) {
    return "Enter a valid email address.";
  }
  return null;
}

export function validateNationalIdLast4(value: string, required = false): string | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return required ? "National ID (last 4 digits) is required." : null;
  }
  if (!/^\d{4}$/.test(trimmed)) {
    return "Enter exactly 4 numeric digits.";
  }
  return null;
}
