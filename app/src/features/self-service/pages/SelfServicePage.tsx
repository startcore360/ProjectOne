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
  const [error, setError] = useState<string | null>(null);

  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [balances, setBalances] = useState<LeaveBalanceItem[]>([]);
  const [requests, setRequests] = useState<LeaveRequestItem[]>([]);
  const [notices, setNotices] = useState<NoticeItem[]>([]);

  const loadData = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [notifs, bals, reqs, nots] = await Promise.all([
        selfServiceApi.getNotifications(tenantId).catch(() => []),
        selfServiceApi.getLeaveBalances(tenantId).catch(() => []),
        selfServiceApi.getLeaveRequests(tenantId).catch(() => []),
        selfServiceApi.getNotices(tenantId).catch(() => []),
      ]);
      setNotifications(Array.isArray(notifs) ? notifs : []);
      setBalances(Array.isArray(bals) ? bals : []);
      setRequests(Array.isArray(reqs) ? reqs : []);
      setNotices(Array.isArray(nots) ? nots : []);
    } catch {
      setError("Failed to load self-service records. Please retry.");
    } finally {
      setLoading(false);
    }
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
        description="Access institutional notices, check remaining leave balances, and review alert notifications [SELF_SERVICE_VIEW]."
      />

      {loading && (
        <div className="flex justify-center p-12">
          <Spinner />
        </div>
      )}

      {error && !loading && (
        <ErrorState
          message={error}
          action={<Button variant="secondary" onClick={loadData}>Retry</Button>}
        />
      )}

      {!loading && !error && (
        <>
          <Tabs
            items={tabItems}
            activeId={activeTab}
            onChange={(id) => setActiveTab(id as "notices" | "leaves" | "notifications")}
            tabRole="button"
          />

          {activeTab === "notices" && (
            <div className="space-y-4">
              {!Array.isArray(notices) || notices.length === 0 ? (
                <EmptyState
                  title="No published notices found."
                  description="Check back later for university-wide announcements."
                />
              ) : (
                notices.map((n) => (
                  <div
                    key={n.noticeId}
                    className="p-5 bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-800 space-y-2"
                  >
                    <div className="flex items-center justify-between">
                      <h3 className="font-semibold text-gray-900 dark:text-gray-100">{n.title}</h3>
                      <div className="flex gap-2">
                        <Badge tone="info">{n.noticeKind}</Badge>
                        <Badge tone="neutral">{n.noticeLevel}</Badge>
                      </div>
                    </div>
                    <p className="text-sm text-gray-600 dark:text-gray-300 whitespace-pre-wrap">{n.body}</p>
                    <div className="text-xs text-gray-400">
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
                <h3 className="text-md font-semibold text-gray-900 dark:text-gray-100 mb-3">Leave Balances</h3>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {!Array.isArray(balances) || balances.length === 0 ? (
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
                <h3 className="text-md font-semibold text-gray-900 dark:text-gray-100 mb-3">Leave Requests</h3>
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
            </div>
          )}

          {activeTab === "notifications" && (
            <div className="space-y-3">
              {!Array.isArray(notifications) || notifications.length === 0 ? (
                <EmptyState
                  title="No notifications."
                  description="You are all caught up."
                />
              ) : (
                notifications.map((n) => (
                  <div
                    key={n.notificationId}
                    className="p-4 bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-800 flex items-start justify-between"
                  >
                    <div>
                      <div className="font-medium text-gray-900 dark:text-gray-100">{n.title}</div>
                      {n.body && <div className="text-sm text-gray-600 dark:text-gray-300 mt-1">{n.body}</div>}
                      <div className="text-xs text-gray-400 mt-2">
                        {new Date(n.createdAt).toLocaleString()} • Trigger: {n.triggerCode}
                      </div>
                    </div>
                    <Badge tone={n.priorityClass === "EMERGENCY" ? "danger" : "neutral"}>
                      {n.priorityClass}
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
