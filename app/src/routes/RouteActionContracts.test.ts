import { describe, expect, it } from "vitest";
import { TENANT_ROUTE_ACTIONS } from "./tenantRouteActions";

// These role/action pairs mirror ref.standard_role_action in sql/05_ref_data.sql.
// The assertions guard the route-level "any of" checks; endpoint authorization remains server-side.
const grants: Record<string, readonly string[]> = {
  UNIVERSITY_ADMIN: ["INSTITUTION_CREATE_DRAFT", "USER_VIEW", "ROLE_ASSIGN", "STRUCTURE_MANAGE"],
  VICE_CHANCELLOR: ["INSTITUTION_ACTIVATE", "USER_VIEW", "STRUCTURE_MANAGE"],
  COLLEGE_ADMIN: ["USER_VIEW", "ROLE_ASSIGN", "STRUCTURE_MANAGE", "SUBJECT_ALLOCATE"],
  SCHOOL_ADMIN: ["USER_VIEW", "ROLE_ASSIGN", "STRUCTURE_MANAGE", "SUBJECT_ALLOCATE"],
  PRINCIPAL_COLLEGE: ["USER_VIEW", "STRUCTURE_MANAGE", "TERM_LOCK"],
  SCHOOL_PRINCIPAL: ["USER_VIEW", "STRUCTURE_MANAGE", "TERM_LOCK"],
  VICE_PRINCIPAL_COLLEGE: ["USER_VIEW", "STRUCTURE_MANAGE"],
  VICE_PRINCIPAL_SCHOOL: ["USER_VIEW", "STRUCTURE_MANAGE"],
  UNIVERSITY_HR_MANAGER: ["USER_VIEW"],
  REGISTRAR: ["USER_VIEW", "STRUCTURE_MANAGE", "PROGRAM_RULES_SET"],
  HEAD_OF_DEPARTMENT: ["SUBJECT_ALLOCATE"],
};

function canOpen(actions: readonly string[], role: string) {
  return actions.some((action) => grants[role]?.includes(action));
}

describe("tenant administration route permission contracts", () => {
  it.each(["UNIVERSITY_ADMIN", "VICE_CHANCELLOR"])(
    "institutions route is reachable by %s",
    (role) => expect(canOpen(TENANT_ROUTE_ACTIONS.institutions, role)).toBe(true),
  );

  it.each([
    "UNIVERSITY_ADMIN", "VICE_CHANCELLOR", "COLLEGE_ADMIN", "SCHOOL_ADMIN",
    "PRINCIPAL_COLLEGE", "SCHOOL_PRINCIPAL", "VICE_PRINCIPAL_COLLEGE",
    "VICE_PRINCIPAL_SCHOOL", "UNIVERSITY_HR_MANAGER", "REGISTRAR",
  ])("users route is reachable by %s", (role) =>
    expect(canOpen(TENANT_ROUTE_ACTIONS.users, role)).toBe(true),
  );

  it.each(["UNIVERSITY_ADMIN", "COLLEGE_ADMIN", "SCHOOL_ADMIN"])(
    "roles route is reachable by %s",
    (role) => expect(canOpen(TENANT_ROUTE_ACTIONS.roles, role)).toBe(true),
  );

  it.each([
    "UNIVERSITY_ADMIN", "VICE_CHANCELLOR", "COLLEGE_ADMIN", "SCHOOL_ADMIN",
    "PRINCIPAL_COLLEGE", "SCHOOL_PRINCIPAL", "VICE_PRINCIPAL_COLLEGE",
    "VICE_PRINCIPAL_SCHOOL", "UNIVERSITY_HR_MANAGER", "REGISTRAR",
  ])("missing-roles route is reachable by %s", (role) =>
    expect(canOpen(TENANT_ROUTE_ACTIONS.missingRoles, role)).toBe(true),
  );

  it.each([
    "UNIVERSITY_ADMIN", "VICE_CHANCELLOR", "COLLEGE_ADMIN", "SCHOOL_ADMIN",
    "PRINCIPAL_COLLEGE", "SCHOOL_PRINCIPAL", "VICE_PRINCIPAL_COLLEGE", "VICE_PRINCIPAL_SCHOOL",
    "REGISTRAR", "HEAD_OF_DEPARTMENT",
  ])("academic-structure route is reachable by %s", (role) =>
    expect(canOpen(TENANT_ROUTE_ACTIONS.academicStructure, role)).toBe(true),
  );

  it.each(["PRINCIPAL_COLLEGE", "SCHOOL_PRINCIPAL"])(
    "term-lock route is reachable by %s",
    (role) => expect(canOpen(TENANT_ROUTE_ACTIONS.termLock, role)).toBe(true),
  );
});
