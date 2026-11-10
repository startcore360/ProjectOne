import { Icon } from "../../../components/ui/Icon";

export interface RoleDirectoryToolsProps {
  query: string;
  onQueryChange: (query: string) => void;
  level: string;
  onLevelChange: (level: string) => void;
}

export function RoleDirectoryTools({
  query,
  onQueryChange,
  level,
  onLevelChange,
}: RoleDirectoryToolsProps) {
  return (
    <div className="role-directory-tools">
      <label className="role-search">
        <Icon name="search" size={17} />
        <input
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="Search role or role code"
        />
      </label>
      <select
        value={level}
        onChange={(e) => onLevelChange(e.target.value)}
        aria-label="Filter by organisational level"
      >
        <option>All</option>
        <option>Platform</option>
        <option>Tenant</option>
        <option>University</option>
        <option>College</option>
        <option>School</option>
        <option>External</option>
      </select>
    </div>
  );
}
