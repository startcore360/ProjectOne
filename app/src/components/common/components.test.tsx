import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { PersonPicker } from "./PersonPicker";
import { SectionPicker } from "./SectionPicker";
import { SubjectAllocationPicker } from "./SubjectAllocationPicker";
import { WeekGrid } from "./WeekGrid";
import { StatusToggleRoster } from "./StatusToggleRoster";
import { FileDrop } from "./FileDrop";
import { WarningsPanel } from "./WarningsPanel";
import { DiffTable } from "./DiffTable";

describe("Sprint 2 shared academic components", () => {
  it("PersonPicker exposes supplied people and reports the selected id", () => {
    const onChange = vi.fn();
    render(<PersonPicker label="Teacher" value="" options={[{ id: "p1", name: "Teacher One" }]} onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("Teacher"), { target: { value: "p1" } });
    expect(onChange).toHaveBeenCalledWith("p1");
  });

  it("SectionPicker reports a selected section", () => {
    const onChange = vi.fn();
    render(<SectionPicker value="" options={[{ id: "s1", label: "Section A" }]} onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("Section"), { target: { value: "s1" } });
    expect(onChange).toHaveBeenCalledWith("s1");
  });

  it("SubjectAllocationPicker reports a selected allocation", () => {
    const onChange = vi.fn();
    render(<SubjectAllocationPicker value="" options={[{ id: "a1", label: "Mathematics" }]} onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("Subject allocation"), { target: { value: "a1" } });
    expect(onChange).toHaveBeenCalledWith("a1");
  });

  it("WeekGrid returns the selected timetable slot", () => {
    const onSelect = vi.fn();
    render(<WeekGrid slots={[{ id: "slot-1", day: "Monday", start: "09:00", end: "10:00", label: "Maths" }]} onSelect={onSelect} />);
    fireEvent.click(screen.getByRole("button", { name: /Monday/ }));
    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: "slot-1" }));
  });

  it("StatusToggleRoster reports a changed attendance status", () => {
    const onChange = vi.fn();
    render(<StatusToggleRoster rows={[{ id: "student-1", name: "Student One", status: "PRESENT" }]} onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("Status for Student One"), { target: { value: "ABSENT" } });
    expect(onChange).toHaveBeenCalledWith("student-1", "ABSENT");
  });

  it("FileDrop rejects a file above its configured limit", () => {
    const onFile = vi.fn();
    render(<FileDrop label="Upload" maxBytes={2} onFile={onFile} />);
    const file = new File(["123"], "test.txt", { type: "text/plain" });
    fireEvent.change(screen.getByLabelText("Upload"), { target: { files: [file] } });
    expect(onFile).toHaveBeenCalledWith(null);
  });

  it("WarningsPanel presents warnings accessibly", () => {
    render(<WarningsPanel warnings={["Resolve timetable clash"]} />);
    expect(screen.getByRole("region", { name: "Please review" })).toBeInTheDocument();
    expect(screen.getByText("Resolve timetable clash")).toBeInTheDocument();
  });

  it("DiffTable displays before and after values", () => {
    render(<DiffTable rows={[{ field: "Room", before: "A1", after: "B2" }]} />);
    expect(screen.getByRole("table")).toHaveTextContent("A1");
    expect(screen.getByRole("table")).toHaveTextContent("B2");
  });
});
