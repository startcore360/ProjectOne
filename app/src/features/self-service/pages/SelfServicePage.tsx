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
  const [loading, setLoading] = useState(true);
  const [errors, setErrors] = useState<{ notifications?: string; balances?: string; requests?: string; notices?: string }>({});

  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [balances, setBalances] = useState<LeaveBalanceItem[]>([]);
  const [requests, setRequests] = useState<LeaveRequestItem[]>([]);
  const [notices, setNotices] = useState<NoticeItem[]>([]);

  const loadData = useCallback(async () => {
    if (!tenantId) {
      const message = "Your account is not linked to an institution. Contact your administrator.";
      setErrors({ notifications: message, balances: message, requests: message, notices: message });
      setLoading(false);
      return;
    }
    setLoading(true);
    const results = await Promise.allSettled([
      selfServiceApi.getNotifications(tenantId),
      selfServiceApi.getLeaveBalances(tenantId),
      selfServiceApi.getLeaveRequests(tenantId),
      selfServiceApi.getNotices(tenantId),
    ]);
    const nextErrors: typeof errors = {};
    if (results[0].status === "fulfilled") setNotifications(Array.isArray(results[0].value) ? results[0].value : []);
    else nextErrors.notifications = "Notifications could not be loaded.";
    if (results[1].status === "fulfilled") setBalances(Array.isArray(results[1].value) ? results[1].value : []);
    else nextErrors.balances = "Leave balances could not be loaded.";
    if (results[2].status === "fulfilled") setRequests(Array.isArray(results[2].value) ? results[2].value : []);
    else nextErrors.requests = "Leave requests could not be loaded.";
    if (results[3].status === "fulfilled") setNotices(Array.isArray(results[3].value) ? results[3].value : []);
    else nextErrors.notices = "Campus notices could not be loaded.";
    setErrors(nextErrors);
    setLoading(false);
  }, [tenantId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

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

      {loading && (
        <div className="flex justify-center p-12">
          <Spinner />
        </div>
      )}

      {!loading && (
        <>
          <Tabs
            items={tabItems}
            activeId={activeTab}
            onChange={(id) => setActiveTab(id as "notices" | "leaves" | "notifications")}
            tabRole="button"
          />

          {activeTab === "notices" && (
            <div className="space-y-4">
              {errors.notices ? <ErrorState message={errors.notices} action={<Button variant="secondary" onClick={loadData}>Retry notices</Button>} /> : !Array.isArray(notices) || notices.length === 0 ? (
                <EmptyState
                  title="No published notices found."
                  description="Check back later for university-wide announcements."
                />
              ) : (
                notices.map((n) => (
                  <div
                    key={n.noticeId}
                    className="self-service-card self-service-card--notice"
                  >
                    <div className="self-service-card__header">
                      <h3 className="self-service-card__title">{n.title}</h3>
                      <div className="self-service-card__badges">
                        <Badge tone="info">{n.noticeKind.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase())}</Badge>
                        <Badge tone="neutral">{n.noticeLevel.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase())}</Badge>
                      </div>
                    </div>
                    <p className="self-service-card__body">{n.body}</p>
                    <div className="self-service-card__meta">
                      Authored by: {n.authoredActiveRole} • {n.publishedAt ? new Date(n.publishedAt).toLocaleDateString() : "Just now"}
                    </div>
                  </div>
                ))
              )}
            </div>
          )}

          {activeTab === "leaves" && (
            <div className="space-y-6">
              <div>
                <h3 className="self-service-section-title">Leave Balances</h3>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {errors.balances ? <div className="col-span-3"><ErrorState message={errors.balances} action={<Button variant="secondary" onClick={loadData}>Retry leave data</Button>} /></div> : !Array.isArray(balances) || balances.length === 0 ? (
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
                {errors.requests ? <ErrorState message={errors.requests} action={<Button variant="secondary" onClick={loadData}>Retry leave data</Button>} /> : <DataTable
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
                </DataTable>}
              </div>
            </div>
          )}

          {activeTab === "notifications" && (
            <div className="space-y-3">
              {errors.notifications ? <ErrorState message={errors.notifications} action={<Button variant="secondary" onClick={loadData}>Retry notifications</Button>} /> : !Array.isArray(notifications) || notifications.length === 0 ? (
                <EmptyState
                  title="No notifications."
                  description="You are all caught up."
                />
              ) : (
                notifications.map((n) => (
                  <div
                    key={n.notificationId}
                    className="self-service-card self-service-card--notification"
                  >
                    <div className="self-service-card__content">
                      <div className="self-service-card__title">{n.title}</div>
                      {n.body && <div className="self-service-card__body">{n.body}</div>}
                      <div className="self-service-card__meta">
                        {new Date(n.createdAt).toLocaleString()}
                      </div>
                    </div>
                    <Badge tone={n.priorityClass === "EMERGENCY" ? "danger" : "neutral"}>
                      {n.priorityClass.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase())}
                    </Badge>
                  </div>
                ))
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
