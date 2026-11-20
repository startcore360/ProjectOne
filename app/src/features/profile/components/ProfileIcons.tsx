import type { ReactNode, SVGProps } from "react";

// Small line icons for the profile page. They draw with currentColor, so every colour still comes
// from the design-system tokens of whatever element holds them (no colour lives in this file).
function Icon({ children, ...rest }: SVGProps<SVGSVGElement> & { children: ReactNode }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  );
}

export const KeyIcon = () => (
  <Icon>
    <circle cx="7.5" cy="15.5" r="4.5" />
    <path d="M10.7 12.3 20 3m-4 4 3 3m-5.5-.5 2 2" />
  </Icon>
);

export const ShieldIcon = () => (
  <Icon>
    <path d="M12 3 4.5 6v5.5c0 4.6 3.1 8.4 7.5 9.5 4.4-1.1 7.5-4.9 7.5-9.5V6z" />
    <path d="m9 12 2 2 4-4" />
  </Icon>
);

export const CodesIcon = () => (
  <Icon>
    <rect x="3.5" y="4.5" width="17" height="15" rx="2.5" />
    <path d="M7.5 9.5h3m3 0h3m-9 5h3m3 0h3" />
  </Icon>
);

export const DevicesIcon = () => (
  <Icon>
    <rect x="2.5" y="4.5" width="14" height="10" rx="1.5" />
    <path d="M6 18.5h7" />
    <rect x="16.5" y="9" width="5" height="10.5" rx="1.2" />
  </Icon>
);

export const PhoneIcon = () => (
  <Icon>
    <rect x="7" y="2.5" width="10" height="19" rx="2" />
    <path d="M11 18h2" />
  </Icon>
);

export const ComputerIcon = () => (
  <Icon>
    <rect x="3" y="4.5" width="18" height="12" rx="1.5" />
    <path d="M8.5 20h7M12 16.5V20" />
  </Icon>
);

export const LockIcon = () => (
  <Icon width="14" height="14">
    <rect x="5" y="10.5" width="14" height="10" rx="2" />
    <path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" />
  </Icon>
);

export const CameraIcon = () => (
  <Icon width="16" height="16">
    <path d="M4 8.5A1.5 1.5 0 0 1 5.5 7h2l1.5-2h6l1.5 2h2A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z" />
    <circle cx="12" cy="13" r="3.2" />
  </Icon>
);

export const PencilIcon = () => (
  <Icon width="16" height="16">
    <path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z" />
    <path d="m13.5 6.5 4 4" />
  </Icon>
);

export const ChevronIcon = () => (
  <Icon width="16" height="16">
    <path d="m9 6 6 6-6 6" />
  </Icon>
);
