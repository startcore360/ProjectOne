import type { ReactNode } from "react";
import DesignSystemButton, {
  type ButtonProps as DesignSystemButtonProps,
} from "../../design-system/components/Button";

/**
 * Backwards-compatible button API for legacy UI imports.
 * Rendering and Stillwater styles are owned by the design-system Button.
 */
export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "accent";
export type ButtonSize = "sm" | "md" | "lg";

export interface ButtonProps extends Omit<DesignSystemButtonProps, "variant" | "size" | "block" | "busy"> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
  children: ReactNode;
}

const variantMap: Record<ButtonVariant, DesignSystemButtonProps["variant"]> = {
  primary: "primary",
  secondary: "secondary",
  ghost: "tertiary",
  danger: "danger",
  accent: "accent",
};

const sizeMap: Record<ButtonSize, DesignSystemButtonProps["size"]> = {
  sm: "sm",
  md: "default",
  lg: "lg",
};

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  fullWidth = false,
  children,
  ...props
}: ButtonProps) {
  return (
    <DesignSystemButton
      {...props}
      variant={variantMap[variant]}
      size={sizeMap[size]}
      busy={loading}
      block={fullWidth}
    >
      {children}
    </DesignSystemButton>
  );
}

export default Button;
