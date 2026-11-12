import TextField, { type TextFieldProps } from "./TextField";

export type DateTimeFieldProps = Omit<TextFieldProps, "type">;

export default function DateTimeField(props: DateTimeFieldProps) {
  return <TextField type="datetime-local" {...props} />;
}
