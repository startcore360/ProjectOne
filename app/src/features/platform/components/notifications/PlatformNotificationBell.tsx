import { useCallback, useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon } from "../../../../components/ui/Icon";
import { Spinner } from "../../../../components/ui/Spinner";
import { platformNotificationsApi, type PlatformBellNotification } from "../../api/platformNotificationsApi";
import { timeAgo } from "../../../notifications/utils/timeAgo";

const MAX_VISIBLE = 10;
const FOCUSABLE_SELECTOR = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function PlatformNotificationBell() {
  const [items, setItems] = useState<PlatformBellNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLElement>(null);
  const panelId = useId();
  const unreadCount = items.filter((item) => item.readAt == null).length;

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const next = await platformNotificationsApi.list();
      setItems(next);
      setError(null);
    } catch {
      setError("Could not load notifications.");
    } finally {
      setLoading(false);
    }
  }, []);

  const close = useCallback(() => {
    setOpen(false);
    buttonRef.current?.focus();
  }, []);

  useEffect(() => {
    void refresh();
    const interval = window.setInterval(() => void refresh(), 30_000);
    return () => window.clearInterval(interval);
  }, [refresh]);

  useEffect(() => {
    if (!open) return undefined;
    panelRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        close();
        return;
      }
      if (event.key !== "Tab" || !panelRef.current) return;
      const focusable = Array.from(panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
      if (!focusable.length) {
        event.preventDefault();
        panelRef.current.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const activeIndex = focusable.indexOf(document.activeElement as HTMLElement);
      if (event.shiftKey && activeIndex <= 0) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (activeIndex === -1 || activeIndex === focusable.length - 1)) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, close]);

  const markRead = async (notificationId: string) => {
    try {
      await platformNotificationsApi.markRead(notificationId);
      setItems((current) => current.map((item) =>
        item.notificationId === notificationId ? { ...item, readAt: new Date().toISOString() } : item));
    } catch {
      setError("Could not update notification.");
    }
  };

  const markAllRead = async () => {
    try {
      await platformNotificationsApi.markAllRead();
      const readAt = new Date().toISOString();
      setItems((current) => current.map((item) => ({ ...item, readAt: item.readAt ?? readAt })));
    } catch {
      setError("Could not update notifications.");
    }
  };

  const visible = items.slice(0, MAX_VISIBLE);
  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        className="app-icon-button notif-bell__button"
        aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : "Notifications"}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        onClick={() => { if (!open) void refresh(); setOpen((value) => !value); }}
      >
        <Icon name="bell" size={20} strokeWidth={1.75} />
        {unreadCount > 0 && <span className="notif-bell__badge" aria-hidden="true">{unreadCount > 99 ? "99+" : unreadCount}</span>}
      </button>
      {open && createPortal(
        <div className="notif-bell__backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) close(); }}>
          <section id={panelId} ref={panelRef} tabIndex={-1} className="notif-bell__panel" role="dialog" aria-modal="true" aria-label="Notifications">
            <span className="notif-bell__grip" aria-hidden="true" />
            <header className="notif-bell__head">
              <div><h2>Notifications</h2></div>
              <div className="notif-bell__head-actions">
                {unreadCount > 0 && <button type="button" className="notif-bell__link" onClick={() => void markAllRead()}>Mark all read</button>}
                <button type="button" className="app-icon-button notif-bell__close" onClick={close} aria-label="Close notifications"><Icon name="x" size={16} /></button>
              </div>
            </header>
            <div className="notif-bell__body">
              {error && <p className="notif-bell__state notif-bell__state--error" role="alert">{error}</p>}
              {loading && items.length === 0 && <p className="notif-bell__state" role="status"><Spinner /> Loading notifications</p>}
              {!loading && !error && items.length === 0 && <p className="notif-bell__state">You have no notifications.</p>}
              {visible.length > 0 && <ul className="notif-bell__list">
                {visible.map((item) => {
                  const unread = item.readAt == null;
                  const priority = item.priorityClass !== "NORMAL";
                  return <li key={item.notificationId} className={"notif-bell__item" + (unread ? " is-unread" : "")}>
                    <div className="notif-bell__text">
                      <strong>{item.title}</strong>
                      {item.body && <span>{item.body}</span>}
                      <small>{priority && <span className="notif-bell__tag">{item.priorityClass.toLowerCase()}</span>}{timeAgo(item.createdAt)}</small>
                    </div>
                    {unread && <button type="button" className="notif-bell__link" aria-label={`Mark "${item.title}" as read`} onClick={() => void markRead(item.notificationId)}>Mark read</button>}
                  </li>;
                })}
              </ul>}
            </div>
          </section>
        </div>,
        document.body,
      )}
    </>
  );
}
