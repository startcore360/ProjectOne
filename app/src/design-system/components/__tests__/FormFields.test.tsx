import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import {
  TextField,
  SelectField,
  TextAreaField,
  DateField,
  DateTimeField,
  CheckboxField,
} from "../index";

describe("Design System Form Controls", () => {
  describe("TextField", () => {
    it("renders label, input, hint, and error with proper accessibility", () => {
      render(
        <TextField
          id="test-text"
          label="Full Name"
          required
          hint="Enter your official legal name"
          error="Name is required"
          placeholder="John Doe"
        />
      );

      const input = screen.getByLabelText(/Full Name/i);
      expect(input).toBeInTheDocument();
      expect(input).toHaveAttribute("required");
      expect(input).toHaveAttribute("aria-invalid", "true");
      expect(screen.getByText("(Required)")).toBeInTheDocument();
      expect(screen.getByText("Enter your official legal name")).toBeInTheDocument();
      expect(screen.getByRole("alert")).toHaveTextContent("Name is required");
    });

    it("suppresses redundant (Required) badge when label already contains an asterisk", () => {
      render(
        <TextField
          id="test-code"
          label="Code *"
          required
        />
      );

      expect(screen.getByLabelText(/Code \*/i)).toBeInTheDocument();
      expect(screen.queryByText("(Required)")).not.toBeInTheDocument();
    });
  });

  describe("SelectField", () => {
    it("renders options from array and fires onChange", () => {
      const handleChange = vi.fn();
      render(
        <SelectField
          id="test-select"
          label="Role"
          required
          value="TEACHER"
          onChange={handleChange}
          options={[
            { value: "STUDENT", label: "Student" },
            { value: "TEACHER", label: "Teacher" },
          ]}
          hint="Select your active persona"
        />
      );

      const select = screen.getByLabelText(/Role/i);
      expect(select).toBeInTheDocument();
      expect(select).toHaveValue("TEACHER");
      expect(screen.getByText("Select your active persona")).toBeInTheDocument();

      fireEvent.change(select, { target: { value: "STUDENT" } });
      expect(handleChange).toHaveBeenCalled();
    });

    it("renders options from children when options prop is omitted", () => {
      render(
        <SelectField id="test-children-select" label="Department">
          <option value="cs">Computer Science</option>
          <option value="ee">Electrical Engineering</option>
        </SelectField>
      );

      expect(screen.getByText("Computer Science")).toBeInTheDocument();
      expect(screen.getByText("Electrical Engineering")).toBeInTheDocument();
    });
  });

  describe("TextAreaField", () => {
    it("renders multiline textarea with rows, hint, and error", () => {
      render(
        <TextAreaField
          id="test-area"
          label="Notes"
          rows={5}
          hint="Max 500 characters"
          error="Notes cannot be empty"
        />
      );

      const area = screen.getByLabelText(/Notes/i);
      expect(area).toBeInTheDocument();
      expect(area).toHaveAttribute("rows", "5");
      expect(area).toHaveAttribute("aria-invalid", "true");
      expect(screen.getByText("Max 500 characters")).toBeInTheDocument();
      expect(screen.getByRole("alert")).toHaveTextContent("Notes cannot be empty");
    });
  });

  describe("DateField and DateTimeField", () => {
    it("renders input type=date for DateField", () => {
      render(<DateField id="test-date" label="Date of Birth" />);
      const input = screen.getByLabelText(/Date of Birth/i);
      expect(input).toHaveAttribute("type", "date");
    });

    it("renders input type=datetime-local for DateTimeField", () => {
      render(<DateTimeField id="test-datetime" label="Scheduled At" />);
      const input = screen.getByLabelText(/Scheduled At/i);
      expect(input).toHaveAttribute("type", "datetime-local");
    });
  });

  describe("CheckboxField", () => {
    it("renders accessible checkbox with label and responds to clicks", () => {
      const handleChange = vi.fn();
      render(
        <CheckboxField
          id="test-check"
          label="I agree to the terms"
          hint="Required for account activation"
          onChange={handleChange}
        />
      );

      const checkbox = screen.getByLabelText(/I agree to the terms/i);
      expect(checkbox).toBeInTheDocument();
      expect(checkbox).not.toBeChecked();
      expect(screen.getByText("Required for account activation")).toBeInTheDocument();

      fireEvent.click(checkbox);
      expect(handleChange).toHaveBeenCalled();
    });
  });
});
