/**
 * The standard otpauth:// set-up link (Key URI format used by Google Authenticator, Microsoft
 * Authenticator, Authy and others). Tapping it on a phone opens the installed authenticator app with
 * the account filled in - the only practical way when the QR code is on the same phone. The values
 * match the policy KeycloakRealmSecurityService applies to every realm: SHA1, 6 digits, 30 seconds.
 * The link is handled by the phone itself; it is never sent to any server.
 */
export function authenticatorLink(secret: string, accountName?: string, issuer = "EDU 360"): string {
  const cleanSecret = secret.replace(/\s+/g, "").replace(/=+$/, "").toUpperCase();
  const cleanIssuer = issuer.replace(/:/g, "").trim() || "EDU 360";
  const cleanAccount = (accountName || "").trim();

  // Key URI Format (RFC 6238 / Key-URI-Format specification):
  // Format: otpauth://totp/{encoded_issuer}:{encoded_account}?secret={secret}&issuer={encoded_issuer}&algorithm=SHA1&digits=6&period=30
  // Note: The colon ':' separating issuer and account in the label path MUST NOT be percent-encoded (%3A),
  // as authenticator apps (Google Authenticator, Microsoft Authenticator, Authy, Apple Passwords)
  // split on the literal ':' to extract the issuer and account name.
  const encodedIssuer = encodeURIComponent(cleanIssuer);
  const label = cleanAccount
    ? `${encodedIssuer}:${encodeURIComponent(cleanAccount)}`
    : encodedIssuer;

  const params = new URLSearchParams({
    secret: cleanSecret,
    issuer: cleanIssuer,
    algorithm: "SHA1",
    digits: "6",
    period: "30",
  });

  return `otpauth://totp/${label}?${params.toString().replace(/\+/g, "%20")}`;
}
