/**
 * The standard otpauth:// set-up link (Key URI format used by Google Authenticator, Microsoft
 * Authenticator, Authy and others). Tapping it on a phone opens the installed authenticator app with
 * the account filled in - the only practical way when the QR code is on the same phone. The values
 * match the policy KeycloakRealmSecurityService applies to every realm: SHA1, 6 digits, 30 seconds.
 * The link is handled by the phone itself; it is never sent to any server.
 */
export function authenticatorLink(secret: string, accountName?: string): string {
  const issuer = "EDU 360";
  const label = encodeURIComponent(accountName ? `${issuer}:${accountName}` : issuer);
  const params = new URLSearchParams({
    secret: secret.replace(/\s+/g, "").toUpperCase(),
    issuer,
    algorithm: "SHA1",
    digits: "6",
    period: "30",
  });
  return `otpauth://totp/${label}?${params.toString().replace(/\+/g, "%20")}`;
}
