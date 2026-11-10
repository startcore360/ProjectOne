import { Icon } from "../../components/ui/Icon";

export interface SampleNoteProps {
  message?: string;
  className?: string;
}

export function SampleNote({
  message = "Sample interface data for layout review. Live records and actions are not connected yet.",
  className = "",
}: SampleNoteProps) {
  return (
    <p className={`platform-sample-note ${className}`.trim()}>
      <span aria-hidden="true">
        <Icon name="shield" size={15} />
      </span>{" "}
      {message}
    </p>
  );
}
