import type { ButtonHTMLAttributes } from "react";
// The one place that renders the .eds-button classes from components.css. Every button in
// the app should go through this component instead of writing className="eds-button ..." by
// hand on a raw <button>, so a change to button styling only ever has to happen here.
export type ButtonVariant = "primary" | "secondary" | "tertiary" | "danger" | "accent";
export type ButtonSize = "sm" | "default" | "lg";

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  block?: boolean;
  busy?: boolean;
};

export default function Button({
  variant = "primary",
  size = "default",
  block = false,
  busy = false,
  className,
  disabled,
  type = "button",
  ...rest
}: ButtonProps) {
  const classes = [
    "eds-button",
    `eds-button--${variant}`,
    size === "sm" && "eds-button--sm",
    size === "lg" && "eds-button--lg",
    block && "eds-button--block",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      {...rest}
    />
  );
}
