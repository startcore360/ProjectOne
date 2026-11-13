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
      className={`platform-tabs ${className}`.trim()}
      style={{
        display: "flex",
        gap: "4px",
        borderBottom: "1px solid var(--color-border)",
        marginBottom: "var(--space-4)",
        overflowX: "auto",
      }}
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
            style={{
              padding: "8px 16px",
              background: "none",
              border: "none",
              borderBottom: isActive ? "2px solid var(--color-brand)" : "2px solid transparent",
              color: isActive ? "var(--color-brand)" : "var(--color-text-muted)",
              fontWeight: isActive ? 600 : 500,
              cursor: tab.disabled ? "not-allowed" : "pointer",
              opacity: tab.disabled ? 0.5 : 1,
              whiteSpace: "nowrap",
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              fontSize: "0.875rem",
              transition: "all 0.15s ease",
            }}
          >
            <span>{tab.label}</span>
            {typeof tab.count === "number" && (
              <span
                style={{
                  fontSize: "0.75rem",
                  padding: "1px 6px",
                  borderRadius: "999px",
                  background: isActive ? "var(--color-brand-muted, #EBF4FF)" : "var(--color-bg-muted, #F1F5F9)",
                  color: isActive ? "var(--color-brand)" : "var(--color-text-muted)",
                }}
              >
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
