import { useCallback, useEffect, useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Badge, Spinner } from "../../../components/ui";
import {
  Button,
  Tabs,
  DataTable,
  MetricCard,
  StatusBadge,
  EmptyState,
} from "../../../design-system/components";
import { ErrorState } from "../../../components/feedback/ErrorState";
import { useAppSelector } from "../../../store/hooks";
import {
  selfServiceApi,
  type NotificationItem,
  type LeaveBalanceItem,
  type LeaveRequestItem,
  type NoticeItem,
} from "../api/selfServiceApi";

export default function SelfServicePage() {
  const authUser = useAppSelector((state) => state.auth.user);
  const tenantId = authUser?.tenantId ?? "";

  const [activeTab, setActiveTab] = useState<"notices" | "leaves" | "notifications">("notices");

  // Each section loads and fails independently so the other tabs remain usable
  // when a single API call returns an error (e.g. leave-balances 403).
  const [notices, setNotices] = useState<NoticeItem[]>([]);
  const [noticesLoading, setNoticesLoading] = useState(true);
  const [noticesError, setNoticesError] = useState<string | null>(null);

  const [balances, setBalances] = useState<LeaveBalanceItem[]>([]);
  const [requests, setRequests] = useState<LeaveRequestItem[]>([]);
  const [leavesLoading, setLeavesLoading] = useState(true);
  const [leavesError, setLeavesError] = useState<string | null>(null);

  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [notificationsLoading, setNotificationsLoading] = useState(true);
  const [notificationsError, setNotificationsError] = useState<string | null>(null);

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

  const loadNotifications = useCallback(async () => {
    if (!tenantId) { setNotificationsError(MISSING_CTX); setNotificationsLoading(false); return; }
    setNotificationsLoading(true);
    setNotificationsError(null);
    try {
      setNotifications(await selfServiceApi.getNotifications(tenantId));
    } catch {
      setNotificationsError("Notifications could not be loaded. Please try again.");
    } finally {
      setNotificationsLoading(false);
    }
  }, [tenantId]);

  useEffect(() => { void loadNotices(); }, [loadNotices]);
  useEffect(() => { void loadLeaves(); }, [loadLeaves]);
  useEffect(() => { void loadNotifications(); }, [loadNotifications]);

  const tabItems = [
    { id: "notices", label: `Campus Notices (${notices.length})` },
    { id: "leaves", label: "Leave Balances & History" },
    { id: "notifications", label: `Notifications (${notifications.length})` },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Self-Service Portal"
        description="Access institutional notices, check remaining leave balances, and review your notifications."
      />

      {/* tabRole="tab" (default) gives correct tablist/tab/tabpanel semantics and aria-controls */}
      <Tabs
        items={tabItems}
        activeId={activeTab}
        onChange={(id) => setActiveTab(id as "notices" | "leaves" | "notifications")}
      />

      {/* Each panel has id="panel-{id}" and role="tabpanel" matching the Tabs component's
          aria-controls="panel-{id}" output so screen readers announce the relationship. */}

      <div
        id="panel-notices"
        role="tabpanel"
        aria-labelledby="tab-notices"
        hidden={activeTab !== "notices"}
        className="space-y-4"
      >
        {noticesLoading && (
          <div className="flex justify-center p-12"><Spinner /></div>
        )}
        {noticesError && !noticesLoading && (
          <ErrorState
            message={noticesError}
            action={<Button variant="secondary" onClick={() => void loadNotices()}>Retry</Button>}
          />
        )}
        {!noticesLoading && !noticesError && (
          notices.length === 0 ? (
            <EmptyState
              title="No published notices found."
              description="Check back later for university-wide announcements."
            />
          ) : (
            notices.map((n) => (
              <div key={n.noticeId} className="self-service-card self-service-card--notice">
                <div className="self-service-card__header">
                  <h3 className="self-service-card__title">{n.title}</h3>
                  <div className="self-service-card__badges">
                    <Badge tone="info">{n.noticeKind.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())}</Badge>
                    <Badge tone="neutral">{n.noticeLevel.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())}</Badge>
                  </div>
                </div>
                <p className="self-service-card__body">{n.body}</p>
                <div className="self-service-card__meta">
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
        className="space-y-6"
      >
        {leavesLoading && (
          <div className="flex justify-center p-12"><Spinner /></div>
        )}
        {leavesError && !leavesLoading && (
          <ErrorState
            message={leavesError}
            action={<Button variant="secondary" onClick={() => void loadLeaves()}>Retry</Button>}
          />
        )}
        {!leavesLoading && !leavesError && (
          <>
            <div>
              <h3 className="self-service-section-title">Leave Balances</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {balances.length === 0 ? (
                  <div className="col-span-3">
                    <EmptyState
                      title="No leave balance records found."
                      description="Your account currently has no allocated leave quotas."
                    />
                  </div>
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
              <h3 className="self-service-section-title">Leave Requests</h3>
              <DataTable
                headers={["Dates", "Days", "Reason", "Status"]}
                empty={requests.length === 0}
                emptyMessage="No leave requests found."
              >
                {requests.map((r) => (
                  <tr key={r.leaveRequestId}>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {r.startDate} to {r.endDate}
                    </td>
                    <td className="px-4 py-3">{r.daysCounted} ({r.unit})</td>
                    <td className="px-4 py-3">{r.reason}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={r.status} />
                    </td>
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
        className="space-y-3"
      >
        {notificationsLoading && (
          <div className="flex justify-center p-12"><Spinner /></div>
        )}
        {notificationsError && !notificationsLoading && (
          <ErrorState
            message={notificationsError}
            action={<Button variant="secondary" onClick={() => void loadNotifications()}>Retry</Button>}
          />
        )}
        {!notificationsLoading && !notificationsError && (
          notifications.length === 0 ? (
            <EmptyState
              title="No notifications."
              description="You are all caught up."
            />
          ) : (
            notifications.map((n) => (
              <div key={n.notificationId} className="self-service-card self-service-card--notification">
                <div className="self-service-card__content">
                  <div className="self-service-card__title">{n.title}</div>
                  {n.body && <div className="self-service-card__body">{n.body}</div>}
                  <div className="self-service-card__meta">
                    {new Date(n.createdAt).toLocaleString()}
                  </div>
                </div>
                <Badge tone={n.priorityClass === "EMERGENCY" ? "danger" : "neutral"}>
                  {n.priorityClass.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())}
                </Badge>
              </div>
            ))
          )
        )}
      </div>
    </div>
  );
}

