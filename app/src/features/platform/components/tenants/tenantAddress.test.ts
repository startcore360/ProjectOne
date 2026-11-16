import { describe, expect, it } from "vitest";
import { tenantLoginAddress } from "./tenantAddress";

describe("tenantLoginAddress", () => {
  it("keeps the dot style used with the hosts file", () => {
    expect(tenantLoginAddress("abc", { protocol: "http:", hostname: "platform.edu360.local", port: "5173" }))
      .toBe("http://abc.edu360.local:5173");
  });

  it("follows the hyphen style used with one DuckDNS name per tenant", () => {
    expect(tenantLoginAddress("abc", { protocol: "http:", hostname: "platform-edu360.duckdns.org", port: "5173" }))
      .toBe("http://abc-edu360.duckdns.org:5173");
  });

  it("omits the port when there is none", () => {
    expect(tenantLoginAddress("abc", { protocol: "https:", hostname: "platform.edu360.com", port: "" }))
      .toBe("https://abc.edu360.com");
  });
});
