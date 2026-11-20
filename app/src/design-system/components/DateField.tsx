import TextField, { type TextFieldProps } from "./TextField";

export type DateFieldProps = Omit<TextFieldProps, "type">;

/** Shared Stillwater date input; keeps date controls aligned with the design-system TextField. */
export default function DateField(props: DateFieldProps) {
  return <TextField type="date" {...props} />;
}
