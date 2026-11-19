// The sign-in address of a new tenant, built from the address the platform console itself is
// open on - so it follows whichever style is in use, without a separate setting:
//   platform.edu360.localhost    -> abc.edu360.localhost    (dot style, no hosts file needed)
//   platform.edu360.local        -> abc.edu360.local        (dot style, hosts file)
//   platform-edu360.duckdns.org  -> abc-edu360.duckdns.org  (hyphen style, one DuckDNS name each)
//   platform.edu360.com          -> abc.edu360.com          (PRODUCTION: wildcard DNS + certificate)
// The backend resolves all of them (LoginTenantResolver, app.keycloak.local-dev-host-suffix).
const PLATFORM_LABEL = "platform";

export function tenantLoginAddress(
  subdomain: string,
  location: Pick<Location, "protocol" | "hostname" | "port">,
): string {
  const host = location.hostname.toLowerCase();
  const port = location.port ? `:${location.port}` : "";
  // The part after "platform" ("." or "-" plus the rest) is the shared ending for every tenant.
  const ending = host.startsWith(PLATFORM_LABEL) && host.length > PLATFORM_LABEL.length
    ? host.slice(PLATFORM_LABEL.length)
    : `.${host.split(".").slice(1).join(".")}`;
  return `${location.protocol}//${subdomain}${ending}${port}`;
}
