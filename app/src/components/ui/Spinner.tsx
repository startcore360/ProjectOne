import { Spinner as DsSpinner, type SpinnerProps } from "../../design-system/components/Spinner";

export type { SpinnerProps };

export function Spinner(props: SpinnerProps) {
  return <DsSpinner {...props} />;
}

export default Spinner;
