import { EmptyState as DsEmptyState, type EmptyStateProps } from "../../design-system/components/EmptyState";

export type { EmptyStateProps };

export function EmptyState(props: EmptyStateProps) {
  return <DsEmptyState {...props} />;
}

export default EmptyState;
