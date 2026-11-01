import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "../../lib/utils";

// A standard clickable button with color styles (variant), sizes, a loading
// spinner, and a full-width option, so every button in the app looks the same.
type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "accent";
type ButtonSize = "sm" | "md" | "lg";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
  children: ReactNode;
}

const variants: Record<ButtonVariant, string> = {
  primary: "eds-button--primary",
  secondary: "eds-button--secondary",
  ghost: "eds-button--tertiary",
  danger: "eds-button--danger",
  accent: "eds-button--accent",
};

const sizes: Record<ButtonSize, string> = {
  sm: "eds-button--sm",
  md: "",
  lg: "eds-button--lg",
};

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  fullWidth = false,
  disabled,
  children,
  className,
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      {...props}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        "eds-button",
        variants[variant],
        sizes[size],
        fullWidth && "eds-button--block",
        className,
      )}
    >
      {loading ? <span className="eds-spinner" aria-hidden="true" /> : null}
      {children}
    </button>
  );
}
