export interface TabItem {
  id: string;
  label: string;
  count?: number;
  disabled?: boolean;
}

export interface TabsProps {
  items: TabItem[];
  activeId: string;
  onChange: (id: string) => void;
  className?: string;
  ariaLabel?: string;
  tabRole?: "tab" | "button";
}

export function Tabs({
  items,
  activeId,
  onChange,
  className = "",
  ariaLabel = "Navigation tabs",
  tabRole = "tab",
}: TabsProps) {
  const isTabList = tabRole === "tab";

  return (
    <div
      role={isTabList ? "tablist" : "group"}
      aria-label={ariaLabel}
      className={`app-tabs ${className}`.trim()}
    >
      {items.map((tab) => {
        const isActive = tab.id === activeId;
        return (
          <button
            key={tab.id}
            role={isTabList ? "tab" : undefined}
            id={`tab-${tab.id}`}
            aria-selected={isTabList ? isActive : undefined}
            aria-pressed={!isTabList ? isActive : undefined}
            aria-controls={isTabList ? `panel-${tab.id}` : undefined}
            tabIndex={isTabList ? (isActive ? 0 : -1) : undefined}
            data-active={isActive}
            disabled={tab.disabled}
            onClick={() => onChange(tab.id)}
            onKeyDown={(event) => {
              if (!isTabList || !["ArrowRight", "ArrowLeft", "Home", "End"].includes(event.key)) return;
              const enabledTabs = Array.from(
                event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>(
                  '[role="tab"]:not(:disabled)',
                ) ?? [],
              );
              if (enabledTabs.length === 0) return;
              const currentIndex = enabledTabs.indexOf(event.currentTarget);
              const nextIndex = event.key === "Home"
                ? 0
                : event.key === "End"
                  ? enabledTabs.length - 1
                  : (currentIndex + (event.key === "ArrowRight" ? 1 : -1) + enabledTabs.length) % enabledTabs.length;
              event.preventDefault();
              enabledTabs[nextIndex].focus();
              onChange(enabledTabs[nextIndex].id.replace(/^tab-/, ""));
            }}
            className="app-tabs__tab"
          >
            <span>{tab.label}</span>
            {typeof tab.count === "number" && (
              <span className="app-tabs__count">
                {tab.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

export default Tabs;
