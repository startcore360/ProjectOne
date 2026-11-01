import { NavLink, Outlet } from "react-router-dom";

const navItems = [
  { label: "Dashboard", to: "/" },
  { label: "Overview", to: "/dashboard" },
];

export default function AppLayout() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <div className="text-lg font-semibold">Edu 360</div>
          <nav className="flex gap-4">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `rounded-md px-3 py-2 text-sm font-medium ${
                    isActive
                      ? "bg-slate-900 text-white"
                      : "text-slate-600 hover:bg-slate-100"
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <div className="mx-auto flex max-w-7xl gap-6 px-6 py-8">
        <aside className="hidden w-64 rounded-xl border border-slate-200 bg-white p-4 lg:block">
          <ul className="space-y-2">
            <li className="rounded-md bg-slate-100 px-3 py-2 text-sm font-medium">
              Main menu
            </li>
            <li className="px-3 py-2 text-sm text-slate-600">Students</li>
            <li className="px-3 py-2 text-sm text-slate-600">Courses</li>
            <li className="px-3 py-2 text-sm text-slate-600">Reports</li>
          </ul>
        </aside>

        <main className="flex-1 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
