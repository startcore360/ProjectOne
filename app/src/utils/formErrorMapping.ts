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

export function normalizeFormErrors(errors: Record<string, string>) {
  return Object.entries(errors).reduce<Record<string, string>>(
    (acc, [key, value]) => {
      const normalizedKey = formErrorMapping[key] ?? key;
      acc[normalizedKey] = value;
      return acc;
    },
    {},
  );
}

export function splitFullName(fullName: string) {
  const [firstName = "", ...rest] = fullName.trim().split(/\s+/);
  return {
    firstName,
    lastName: rest.join(" "),
  };
}
