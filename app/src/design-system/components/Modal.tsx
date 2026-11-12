import { useEffect, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Icon } from "../../components/ui/Icon";
import Kicker from "./Kicker";

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

export type ModalSize = "sm" | "md" | "lg" | "xl";

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  titleId?: string;
  kicker?: string;
  description?: string;
  footer?: ReactNode;
  children: ReactNode;
  size?: ModalSize;
  className?: string;
  closeOnBackdrop?: boolean;
}

const sizeClasses: Record<ModalSize, string> = {
  sm: "max-w-[440px]",
  md: "max-w-[560px]",
  lg: "max-w-[720px]",
  xl: "max-w-[900px]",
};

export function Modal({
  isOpen,
  onClose,
  title,
  titleId: customTitleId,
  kicker,
  description,
  footer,
  children,
  size = "md",
  className = "",
  closeOnBackdrop = true,
}: ModalProps) {
  const contentRef = useRef<HTMLElement>(null);
  const reactId = useId();
  const titleId = customTitleId ?? `modal-title-${reactId}`;
  const descriptionId = `modal-description-${reactId}`;

  // Keep keyboard focus inside the dialog, including when focus enters from outside it.
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
        return;
      }
      if (e.key !== "Tab" || !contentRef.current) return;

      const focusable = Array.from(
        contentRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR),
      );
      if (focusable.length === 0) {
        e.preventDefault();
        contentRef.current.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const activeIndex = focusable.indexOf(document.activeElement as HTMLElement);

      if (e.shiftKey && activeIndex <= 0) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && (activeIndex === -1 || activeIndex === focusable.length - 1)) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  // Move focus into the dialog when it opens and restore it when it closes.
  useEffect(() => {
    if (!isOpen) return;
    const previouslyFocused = document.activeElement as HTMLElement | null;
    const focusable = contentRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
    (focusable?.[0] ?? contentRef.current)?.focus();

    return () => {
      previouslyFocused?.focus();
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const modalElement = (
    <div
      className="platform-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (closeOnBackdrop && e.target === e.currentTarget) {
          onClose();
        }
      }}
    >
      <section
        ref={contentRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-describedby={description ? descriptionId : undefined}
        data-size={size}
        tabIndex={-1}
        className={`platform-modal ${sizeClasses[size]} ${className}`.trim()}
      >
        {(title || kicker || description) && (
          <header>
            <div>
              {kicker && <Kicker>{kicker}</Kicker>}
              {title && <h2 id={titleId}>{title}</h2>}
              {description && <p id={descriptionId}>{description}</p>}
            </div>
            <button
              type="button"
              className="platform-icon-button platform-modal__close"
              onClick={onClose}
              aria-label="Close"
            >
              <Icon name="x" size={16} />
            </button>
          </header>
        )}
        <div className="platform-modal__body">{children}</div>
        {footer && <footer className="platform-modal__footer">{footer}</footer>}
      </section>
    </div>
  );

  if (typeof document !== "undefined") {
    return createPortal(modalElement, document.body);
  }
  return modalElement;
}

export default Modal;
