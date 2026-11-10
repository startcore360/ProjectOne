import { Badge as DsBadge, type BadgeProps } from "../../design-system/components/Badge";

export type { BadgeProps };

export function Badge(props: BadgeProps) {
  return <DsBadge {...props} />;
}

export default Badge;
