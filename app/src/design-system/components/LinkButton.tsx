import type { ButtonHTMLAttributes } from "react";
// The one place that renders the .eds-link-button class: a text-styled, no-background button
// for secondary actions ("Forgot password?", "Cancel and sign out") that shouldn't look like a
// primary/secondary button but still need to be a real <button>, not a link with no href.
export default function LinkButton({
  className,
  type = "button",
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement>) {
  const classes = ["eds-link-button", className].filter(Boolean).join(" ");
  return <button type={type} className={classes} {...rest} />;
}
