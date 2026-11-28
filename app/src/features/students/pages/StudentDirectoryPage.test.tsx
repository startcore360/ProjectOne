import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { StudentDetailPage, StudentDirectoryPage } from "./StudentDirectoryPage";
import { studentApi } from "../api/studentApi";

vi.mock("../api/studentApi", () => ({
  studentApi: {
    list: vi.fn(), get: vi.fn(), update: vi.fn(), changeStatus: vi.fn(),
    guardians: vi.fn(), guardianPreferencesList: vi.fn(), guardianPreferences: vi.fn(),
    linkGuardian: vi.fn(), notes: vi.fn(), holds: vi.fn(), addNote: vi.fn(),
  },
}));

const store = configureStore({ reducer: { auth: (state = { user: { tenantId: "tenant-1" } }) => state } });

describe("StudentDirectoryPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(studentApi.list).mockResolvedValue({ data: { items: [{ studentProfileId: "student-1", studentCode: "S-001", fullName: "Asha Rao", status: "ACTIVE", institutionId: "inst-1" }], hasMore: false } } as never);
    vi.mocked(studentApi.get).mockResolvedValue({ data: { full_name: "Asha Rao", student_code: "S-001", status: "ACTIVE" } } as never);
    vi.mocked(studentApi.guardians).mockResolvedValue({ data: [] } as never);
    vi.mocked(studentApi.guardianPreferencesList).mockResolvedValue({ data: [] } as never);
    vi.mocked(studentApi.notes).mockResolvedValue({ data: [] } as never);
    vi.mocked(studentApi.holds).mockResolvedValue({ data: [] } as never);
    vi.mocked(studentApi.update).mockResolvedValue({ data: {} } as never);
    vi.mocked(studentApi.linkGuardian).mockResolvedValue({ data: { studentGuardianLinkId: "link-1" } } as never);
    vi.mocked(studentApi.guardianPreferences).mockResolvedValue({ data: {} } as never);
  });

  it("renders the directory and navigates from a student row", async () => {
    render(<Provider store={store}><MemoryRouter initialEntries={["/tenants/students"]}><Routes>
      <Route path="/tenants/students" element={<StudentDirectoryPage />} />
      <Route path="/tenants/students/:id" element={<div>Student detail route</div>} />
    </Routes></MemoryRouter></Provider>);
    expect(await screen.findByText("Asha Rao")).toBeTruthy();
    fireEvent.click(screen.getByRole("link", { name: /open asha rao/i }));
    expect(await screen.findByText("Student detail route")).toBeTruthy();
  });

  it("sends the selected status filter to the directory API", async () => {
    render(<Provider store={store}><MemoryRouter><StudentDirectoryPage /></MemoryRouter></Provider>);
    await screen.findByText("Asha Rao");
    fireEvent.change(screen.getByLabelText("Status"), { target: { value: "SUSPENDED" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply filters" }));
    await waitFor(() => expect(studentApi.list).toHaveBeenLastCalledWith("tenant-1", expect.objectContaining({ status: "SUSPENDED" })));
  });

  it("edits and saves a student profile through the profile API", async () => {
    render(<Provider store={store}><MemoryRouter initialEntries={["/tenants/students/student-1"]}><Routes>
      <Route path="/tenants/students/:studentId" element={<StudentDetailPage />} />
    </Routes></MemoryRouter></Provider>);
    expect(await screen.findByRole("heading", { name: "Asha Rao" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Edit profile" }));
    fireEvent.change(screen.getByLabelText("full name"), { target: { value: "Asha R." } });
    fireEvent.click(screen.getByRole("button", { name: "Save profile" }));
    await waitFor(() => expect(studentApi.update).toHaveBeenCalledWith("tenant-1", "student-1", expect.objectContaining({ full_name: "Asha R." })));
  });

  it("opens student details, adds a guardian, saves preferences and refreshes the guardian list", async () => {
    const user = userEvent.setup();
    render(<Provider store={store}><MemoryRouter initialEntries={["/tenants/students/student-1"]}><Routes>
      <Route path="/tenants/students/:studentId" element={<StudentDetailPage />} />
    </Routes></MemoryRouter></Provider>);
    await screen.findByRole("heading", { name: "Asha Rao" });
    await user.click(screen.getByRole("button", { name: "Add guardian" }));
    await user.type(screen.getByLabelText("Full name (Required)"), "Meera Rao");
    await user.type(screen.getByLabelText(/Mobile.*\(Required\)/i), "+919876543210");
    await user.type(screen.getByLabelText("Email"), "meera@example.com");
    await user.selectOptions(screen.getByLabelText("Relationship"), "MOTHER");
    await user.click(screen.getByLabelText("Emergency contact"));
    await user.click(screen.getByRole("button", { name: "Save guardian" }));

    await waitFor(() => expect(studentApi.linkGuardian).toHaveBeenCalledWith("tenant-1", "student-1", {
      fullName: "Meera Rao", mobile: "+919876543210", email: "meera@example.com", relationship: "MOTHER", isPrimary: true,
    }));
    expect(studentApi.guardianPreferences).toHaveBeenCalledWith("tenant-1", "student-1", "link-1", {
      contactAuthorized: true, emergencyContact: true, primary: true,
    });
    await waitFor(() => expect(studentApi.guardians).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
