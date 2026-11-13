import { describe, expect, it } from "vitest";
import { TENANT_ROUTE_ACTIONS } from "./tenantRouteActions";

describe("tenant route action coverage", () => {
  it("allows HR roles with read or manage grants to open staff records", () => {
    expect(TENANT_ROUTE_ACTIONS.staffRecords).toContain("STAFF_RECORD_VIEW");
    expect(TENANT_ROUTE_ACTIONS.staffRecords).toContain("STAFF_RECORD_MANAGE");
  });

  it("keeps institution, role and academic structure route gates action-backed", () => {
    expect(TENANT_ROUTE_ACTIONS.institutions).toContain("INSTITUTION_CREATE_DRAFT");
    expect(TENANT_ROUTE_ACTIONS.roles).toContain("ROLE_ASSIGN");
    expect(TENANT_ROUTE_ACTIONS.academicStructure).toContain("STRUCTURE_MANAGE");
  });

  it("keeps operational assignment and governance route gates distinct", () => {
    expect(TENANT_ROUTE_ACTIONS.hodAssignment).toEqual(["HOD_ASSIGN"]);
    expect(TENANT_ROUTE_ACTIONS.mentorAssignment).toEqual(["MENTOR_ASSIGN"]);
    expect(TENANT_ROUTE_ACTIONS.classTeacherAssignment).toEqual(["CLASS_TEACHER_ASSIGN"]);
    expect(TENANT_ROUTE_ACTIONS.academicGovernance).toContain("PROMOTION_RUN");
  });
});
