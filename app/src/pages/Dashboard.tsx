import { useAppSelector } from "../store/hooks";

export default function DashboardPage() {
  const { user } = useAppSelector((state) => state.auth);
  const roleLabel = user?.role ?? "STUDENT";

  return (
    <div className="space-y-6">
      <div>
        //added 
        <p className="text-sm font-medium uppercase tracking-[0.2em] text-emerald-600">
          Overview
        </p>
        <h1 className="mt-2 text-3xl font-bold text-slate-900">
          Welcome, {user?.name ?? "User"}
        </h1>
        <p className="mt-2 text-sm text-slate-500">Role: {roleLabel}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-5">
          <p className="text-sm text-slate-500">Courses</p>
          <p className="mt-2 text-3xl font-bold">
            {roleLabel === "TEACHER" ? "12" : "6"}
          </p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-5">
          <p className="text-sm text-slate-500">Attendance</p>
          <p className="mt-2 text-3xl font-bold">
            {roleLabel === "TEACHER" ? "96%" : "94%"}
          </p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-5">
          <p className="text-sm text-slate-500">Performance</p>
          <p className="mt-2 text-3xl font-bold">
            {roleLabel === "TEACHER" ? "88%" : "90%"}
          </p>
        </div>
      </div>
    </div>
  );
}
