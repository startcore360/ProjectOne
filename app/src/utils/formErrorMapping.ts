// Different backend error messages sometimes spell a field name differently
// (e.g. "eMail" vs "email"). This table maps all the variants to one name our
// forms actually use, so error messages always land on the right field.
export const formErrorMapping: Record<string, string> = {
  email: "email",
  eMail: "email",
  password: "password",
  firstName: "firstName",
  firstname: "firstName",
  lastName: "lastName",
  lastname: "lastName",
  fullName: "fullName",
  name: "fullName",
};

// Takes the backend's field-error messages and renames the fields using the
// mapping above, so a form can look up errors by its own field names.
export function normalizeFormErrors(errors: Record<string, string>) {
  return Object.entries(errors).reduce<Record<string, string>>((acc, [key, value]) => {
    const normalizedKey = formErrorMapping[key] ?? key;
    acc[normalizedKey] = value;
    return acc;
  }, {});
}

// Splits one "full name" text box into a first name and a last name, for
// forms that store those as two separate fields.
export function splitFullName(fullName: string) {
  const [firstName = "", ...rest] = fullName.trim().split(/\s+/);
  return {
    firstName,
    lastName: rest.join(" "),
  };
}
