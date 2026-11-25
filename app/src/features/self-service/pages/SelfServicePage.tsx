import { useCallback, useEffect, useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Badge } from "../../../components/ui";
import {
  Button,
  Tabs,
  DataTable,
  MetricCard,
  StatusBadge,
  EmptyState,
  SkeletonCard,
  SkeletonMetricCard,
  SkeletonTable,
  SkeletonText,
} from "../../../design-system/components";
import { ErrorState } from "../../../components/feedback/ErrorState";
import { useAppSelector } from "../../../store/hooks";
import { useNotifications } from "../../notifications/hooks/useNotifications";
import {
  selfServiceApi,
  type LeaveBalanceItem,
  type LeaveRequestItem,
  type NoticeItem,
} from "../api/selfServiceApi";

export default function SelfServicePage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId ?? "");

  const [activeTab, setActiveTab] = useState<"notices" | "leaves" | "notifications">("notices");
  const [loadedTabs, setLoadedTabs] = useState<Set<string>>(new Set(["notices"]));

  // Each section loads and fails independently so the other tabs remain usable
  // when a single API call returns an error (e.g. leave-balances 403).
  const [notices, setNotices] = useState<NoticeItem[]>([]);
  const [noticesLoading, setNoticesLoading] = useState(true);
  const [noticesError, setNoticesError] = useState<string | null>(null);

  const [balances, setBalances] = useState<LeaveBalanceItem[]>([]);
  const [requests, setRequests] = useState<LeaveRequestItem[]>([]);
  const [leavesLoading, setLeavesLoading] = useState(true);
  const [leavesError, setLeavesError] = useState<string | null>(null);

  const {
    items: notifications,
    loading: notificationsLoading,
    error: notificationsError,
    refresh: refreshNotifications,
    markRead,
  } = useNotifications(tenantId);

  const MISSING_CTX = "Your account is not linked to an institution. Contact your administrator.";

  const loadNotices = useCallback(async () => {
    if (!tenantId) { setNoticesError(MISSING_CTX); setNoticesLoading(false); return; }
    setNoticesLoading(true);
    setNoticesError(null);
    try {
      setNotices(await selfServiceApi.getNotices(tenantId));
    } catch {
      setNoticesError("Campus notices could not be loaded. Please try again.");
    } finally {
      setNoticesLoading(false);
    }
  }, [tenantId]);

  const loadLeaves = useCallback(async () => {
    if (!tenantId) { setLeavesError(MISSING_CTX); setLeavesLoading(false); return; }
    setLeavesLoading(true);
    setLeavesError(null);
    try {
      const [bals, reqs] = await Promise.all([
        selfServiceApi.getLeaveBalances(tenantId),
        selfServiceApi.getLeaveRequests(tenantId),
      ]);
      setBalances(bals);
      setRequests(reqs);
    } catch {
      setLeavesError("Leave records could not be loaded. Please try again.");
    } finally {
      setLeavesLoading(false);
    }
  }, [tenantId]);

  // Load notices on mount (default tab). Load other tabs only on first activation.
  useEffect(() => { void loadNotices(); }, [loadNotices]);
  useEffect(() => { if (loadedTabs.has("leaves")) void loadLeaves(); }, [loadLeaves, loadedTabs]);

  const tabItems = [
    { id: "notices", label: `Campus Notices (${notices.length})` },
    { id: "leaves", label: "Leave Balances & History" },
    { id: "notifications", label: `Notifications (${notifications.length})` },
  ];

  return (
    <div className="app-page">
      <PageHeader
        title="Self-Service Portal"
        description="Access institutional notices, check remaining leave balances, and review your notifications."
      />

      {/* tabRole="tab" (default) gives correct tablist/tab/tabpanel semantics and aria-controls */}
      <Tabs
        items={tabItems}
        activeId={activeTab}
        onChange={(id) => {
          const tab = id as "notices" | "leaves" | "notifications";
          setActiveTab(tab);
          setLoadedTabs((prev) => { const next = new Set(prev); next.add(tab); return next; });
        }}
      />

      {/* Each panel has id="panel-{id}" and role="tabpanel" matching the Tabs component's
          aria-controls="panel-{id}" output so screen readers announce the relationship. */}

      <div
        id="panel-notices"
        role="tabpanel"
        aria-labelledby="tab-notices"
        hidden={activeTab !== "notices"}
        className="app-stack"
      >
        {noticesLoading && notices.length === 0 && (
          <div className="app-stack" role="status" aria-busy="true" aria-label="Loading notices">
            <span className="sr-only">Loading notices...</span>
            {Array.from({ length: 3 }, (_, index) => <SkeletonCard key={index} />)}
          </div>
        )}
        {noticesError && !noticesLoading && (
          <ErrorState
            message={noticesError}
            action={<Button variant="secondary" onClick={() => void loadNotices()}>Retry</Button>}
          />
        )}
        {(!noticesLoading || notices.length > 0) && !noticesError && (
          notices.length === 0 ? (
            <EmptyState
              title="No published notices found."
              description="Check back later for university-wide announcements."
            />
          ) : (
            notices.map((n) => (
              <div key={n.noticeId} className="app-card app-card--notice">
                <div className="app-card__header">
                  <h3 className="app-card__title">{n.title}</h3>
                  <div className="app-card__badges">
                    <Badge tone="info">{n.noticeKind.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())}</Badge>
                    <Badge tone="neutral">{n.noticeLevel.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())}</Badge>
                  </div>
                </div>
                <p className="app-card__body">{n.body}</p>
                {n.requiresAcknowledgment && !n.acknowledged && n.institutionId && (
                  <Button variant="secondary" onClick={async () => { try { await selfServiceApi.acknowledgeNotice(tenantId, n.institutionId!, n.noticeId); await loadNotices(); } catch { setNoticesError("Notice acknowledgment could not be saved. Please try again."); } }}>Acknowledge</Button>
                )}
                <div className="app-card__meta">
                  Authored by: {n.authoredActiveRole} • {n.publishedAt ? new Date(n.publishedAt).toLocaleDateString() : "Just now"}
                </div>
              </div>
            ))
          )
        )}
      </div>

      <div
        id="panel-leaves"
        role="tabpanel"
        aria-labelledby="tab-leaves"
        hidden={activeTab !== "leaves"}
        className="app-stack"
      >
        {leavesLoading && balances.length === 0 && requests.length === 0 && (
          <div className="app-stack" role="status" aria-busy="true" aria-label="Loading leave information">
            <span className="sr-only">Loading leave information...</span>
            <div className="kpi-grid" aria-hidden="true">
              {Array.from({ length: 3 }, (_, index) => <SkeletonMetricCard key={index} />)}
            </div>
            <SkeletonTable columns={4} rows={4} />
          </div>
        )}
        {leavesError && !leavesLoading && (
          <ErrorState
            message={leavesError}
            action={<Button variant="secondary" onClick={() => void loadLeaves()}>Retry</Button>}
          />
        )}
        {(!leavesLoading || balances.length > 0 || requests.length > 0) && !leavesError && (
          <>
            <div>
              <h3 className="app-section-title">Leave Balances</h3>
              <div className="kpi-grid">
                {balances.length === 0 ? (
                  <EmptyState
                    title="No leave balance records found."
                    description="Your account currently has no allocated leave quotas."
                  />
                ) : (
                  balances.map((b) => (
                    <MetricCard
                      key={b.leaveBalanceId}
                      label={`Year ${b.leaveYear}`}
                      value={b.remainingDays}
                      unit="days left"
                      context={`Credited: ${b.creditedDays} • Used: ${b.usedDays}`}
                      mark="clock"
                    />
                  ))
                )}
              </div>
            </div>
            <div>
              <h3 className="app-section-title">Leave Requests</h3>
              <DataTable
                headers={["Dates", "Days", "Reason", "Status"]}
                empty={requests.length === 0}
                emptyMessage="No leave requests found."
              >
                {requests.map((r) => (
                  <tr key={r.leaveRequestId}>
                    <td style={{ whiteSpace: "nowrap" }}>{r.startDate} to {r.endDate}</td>
                    <td>{r.daysCounted} ({r.unit})</td>
                    <td>{r.reason}</td>
                    <td><StatusBadge status={r.status} /></td>
                  </tr>
                ))}
              </DataTable>
            </div>
          </>
        )}
      </div>

      <div
        id="panel-notifications"
        role="tabpanel"
        aria-labelledby="tab-notifications"
        hidden={activeTab !== "notifications"}
        className="app-stack--sm"
      >
        {notificationsLoading && notifications.length === 0 && (
          <div className="app-stack--sm" role="status" aria-busy="true" aria-label="Loading notifications">
            <span className="sr-only">Loading notifications...</span>
            {Array.from({ length: 3 }, (_, index) => (
              <SkeletonCard key={index}>
                <SkeletonText height="1rem" width="60%" />
                <SkeletonText lines={2} height="0.875rem" />
              </SkeletonCard>
            ))}
          </div>
        )}
        {notificationsError && !notificationsLoading && (
          <ErrorState
            message={notificationsError}
            action={<Button variant="secondary" onClick={() => void refreshNotifications()}>Retry</Button>}
          />
        )}
        {(!notificationsLoading || notifications.length > 0) && !notificationsError && (
          notifications.length === 0 ? (
            <EmptyState
              title="No notifications."
              description="You are all caught up."
            />
          ) : (
            notifications.map((n) => {
              const unread = n.readAt === null || n.readAt === undefined;
              return (
                <div
                  key={n.notificationId}
                  className={"app-card app-card--notification" + (unread ? " is-unread" : "")}
                >
                  <div className="app-card__content">
                    <div className="app-card__title">
                      {n.title}
                      {unread && (
                        <span
                          style={{
                            marginLeft: "var(--space-2)",
                            fontSize: "var(--text-xs)",
                            color: "var(--color-primary-600)",
                          }}
                        >
                          • Unread
                        </span>
                      )}
                    </div>
                    {n.body && <div className="app-card__body">{n.body}</div>}
                    <div className="app-card__meta">
                      {new Date(n.createdAt).toLocaleString()}
                      {unread && (
                        <button
                          type="button"
                          className="notif-bell__link"
                          style={{ marginLeft: "var(--space-2)" }}
                          onClick={() => void markRead(n.notificationId)}
                        >
                          Mark read
                        </button>
                      )}
                    </div>
                  </div>
                  <Badge tone={n.priorityClass === "EMERGENCY" ? "danger" : "neutral"}>
                    {n.priorityClass.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())}
                  </Badge>
                </div>
              );
            })
          )
        )}
      </div>
    </div>
  );
}
