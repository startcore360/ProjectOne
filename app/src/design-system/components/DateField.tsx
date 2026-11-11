import TextField, { type TextFieldProps } from "./TextField";

export type DateFieldProps = Omit<TextFieldProps, "type">;

export default function DateField(props: DateFieldProps) {
  return <TextField type="date" {...props} />;
}
