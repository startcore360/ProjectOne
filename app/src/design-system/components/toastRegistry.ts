import type { ReactNode } from "react";

export type ToastVariant = "info" | "success" | "warning" | "error" | "neutral";

export type ToastAction = {
  label: string;
  onClick: () => void;
};

export type ToastItem = {
  id: string;
  variant: ToastVariant;
  title?: string;
  description?: string;
  content?: ReactNode;
  action?: ToastAction;
  showAction?: boolean;
  alertDuration?: number;
  onDismiss?: () => void;
};

export type ToastListener = (toasts: ToastItem[]) => void;

class ToastRegistry {
  private toasts: ToastItem[] = [];
  private listeners: Set<ToastListener> = new Set();
  private nextId = 1;

  public get(): ToastItem[] {
    return [...this.toasts];
  }

  public subscribe(listener: ToastListener): () => void {
    this.listeners.add(listener);
    listener(this.get());
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    const current = this.get();
    for (const listener of this.listeners) {
      listener(current);
    }
  }

  public show(options: Omit<ToastItem, "id"> & { id?: string }): string {
    const id = options.id ?? `toast-${Date.now()}-${this.nextId++}`;
    const item: ToastItem = {
      ...options,
      id,
      alertDuration: options.alertDuration ?? (options.variant === "error" ? 6000 : 4000),
    };

    // Keep up to 3 toasts active at once
    this.toasts = [...this.toasts.filter((t) => t.id !== id), item].slice(-3);
    this.notify();
    return id;
  }

  public hide(id?: string) {
    if (!id) {
      // Hide the latest
      this.toasts = this.toasts.slice(0, -1);
    } else {
      this.toasts = this.toasts.filter((t) => t.id !== id);
    }
    this.notify();
  }

  public clear() {
    this.toasts = [];
    this.notify();
  }
}

export const toastRegistry = new ToastRegistry();

/**
 * Convenient programmatic helper for triggering toasts anywhere in the application.
 * E.g. toast.success("Term locked", "The term is now frozen.");
 */
export const toast = {
  show: (options: Omit<ToastItem, "id"> & { id?: string }) => toastRegistry.show(options),
  success: (title: string, description?: string, options?: Partial<ToastItem>) =>
    toastRegistry.show({ variant: "success", title, description, ...options }),
  error: (title: string, description?: string, options?: Partial<ToastItem>) =>
    toastRegistry.show({ variant: "error", title, description, ...options }),
  warning: (title: string, description?: string, options?: Partial<ToastItem>) =>
    toastRegistry.show({ variant: "warning", title, description, ...options }),
  info: (title: string, description?: string, options?: Partial<ToastItem>) =>
    toastRegistry.show({ variant: "info", title, description, ...options }),
  neutral: (title: string, description?: string, options?: Partial<ToastItem>) =>
    toastRegistry.show({ variant: "neutral", title, description, ...options }),
  dismiss: (id?: string) => toastRegistry.hide(id),
  clear: () => toastRegistry.clear(),
};
