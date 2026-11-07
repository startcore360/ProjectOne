import { ErrorState as DsErrorState, type ErrorStateProps } from "../../design-system/components/ErrorState";

export type { ErrorStateProps };

export function ErrorState(props: ErrorStateProps) {
  return <DsErrorState {...props} />;
}

export default ErrorState;
