import { useCallback, useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { Icon } from "../../../components/ui/Icon";
import { Spinner } from "../../../components/ui/Spinner";
import { useAppSelector } from "../../../store/hooks";
import { useNotifications } from "../hooks/useNotifications";
import { timeAgo } from "../utils/timeAgo";

const MAX_VISIBLE = 10;
const FOCUSABLE_SELECTOR = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Header bell for tenant users (staff, students, parents). Platform-console users have no
 * tenant notifications and never see it. The server owns the data and the read state; this
 * component only shows what `GET /me/notifications` returns.
 *
 * Layout follows the app's own overlay pattern (D10 openMore): on phones the list opens as a
 * bottom sheet over a dimmed backdrop; on larger screens it is a panel under the top bar with
 * a transparent backdrop that closes it. It is rendered in a portal so no parent can clip it.
 */
export function NotificationBell() {
  const user = useAppSelector((s) => s.auth.user);
  const tenantId = user?.tenantId ?? null;
  const { items, loading, error, hasReadState, unreadCount, pendingApprovalCount, markRead, markAllRead, refresh } = useNotifications(tenantId);
  const [open, setOpen] = useState(false);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLElement>(null);
  const panelId = useId();

  const close = useCallback(() => {
    setOpen(false);
    buttonRef.current?.focus();
  }, []);

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
      if (focusable.length === 0) {
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

  if (!tenantId) return null;

  const hasUnread = hasReadState && unreadCount > 0;
  const labelParts = [hasUnread ? `${unreadCount} unread` : null, pendingApprovalCount > 0 ? `${pendingApprovalCount} pending approvals` : null].filter(Boolean);
  const label = labelParts.length ? `Notifications, ${labelParts.join(", ")}` : "Notifications";
  const badgeCount = unreadCount + pendingApprovalCount;
  const visible = items.slice(0, MAX_VISIBLE);

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        className="platform-icon-button notif-bell__button"
        aria-label={label}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        onClick={() => { if (!open) void refresh(); setOpen((v) => !v); }}
      >
        <Icon name="bell" size={20} strokeWidth={1.75} />
        {badgeCount > 0 && <span className="notif-bell__badge" aria-hidden="true">{badgeCount > 99 ? "99+" : badgeCount}</span>}
      </button>
      {open && createPortal(
        <div className="notif-bell__backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) close(); }}>
          <section id={panelId} ref={panelRef} tabIndex={-1} className="notif-bell__panel" role="dialog" aria-modal="true" aria-label="Notifications">
            <span className="notif-bell__grip" aria-hidden="true" />
            <header className="notif-bell__head">
              <div><h2>Notifications</h2>{pendingApprovalCount > 0 && <p className="notif-bell__state" role="status">{pendingApprovalCount} approval{pendingApprovalCount === 1 ? "" : "s"} awaiting your decision</p>}</div>
              <div className="notif-bell__head-actions">
                {hasUnread && <button type="button" className="notif-bell__link" onClick={() => void markAllRead()}>Mark all read</button>}
                <button type="button" className="platform-icon-button notif-bell__close" onClick={close} aria-label="Close notifications">
                  <Icon name="x" size={16} />
                </button>
              </div>
            </header>
            <div className="notif-bell__body">
              {error && <p className="notif-bell__state notif-bell__state--error" role="alert">{error}</p>}
              {loading && items.length === 0 && <p className="notif-bell__state" role="status"><Spinner /> Loading notifications</p>}
              {!loading && !error && items.length === 0 && <p className="notif-bell__state">You have no notifications.</p>}
              {visible.length > 0 && (
                <ul className="notif-bell__list">
                  {visible.map((n) => {
                    const unread = hasReadState && (n.readAt === null || n.readAt === undefined);
                    const priority = n.priorityClass !== "NORMAL";
                    return (
                      <li key={n.notificationId} className={"notif-bell__item" + (unread ? " is-unread" : "")}>
                        <div className="notif-bell__text">
                          <strong>{n.title}</strong>
                          {n.body && <span>{n.body}</span>}
                          <small>
                            {priority && <span className="notif-bell__tag">{n.priorityClass.toLowerCase()}</span>}
                            {timeAgo(n.createdAt)}
                          </small>
                        </div>
                        {unread && (
                          <button type="button" className="notif-bell__link" aria-label={`Mark "${n.title}" as read`} onClick={() => void markRead(n.notificationId)}>
                            Mark read
                          </button>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
            <footer className="notif-bell__foot">
              <Link to="/self-service" onClick={() => setOpen(false)}>View all in Self-service</Link>
            </footer>
          </section>
        </div>,
        document.body,
      )}
    </>
  );
}
