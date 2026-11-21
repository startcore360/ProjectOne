import { Icon } from "../../components/ui/Icon";

export interface SearchBoxProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
}

export function SearchBox({
  value,
  onChange,
  placeholder = "Search records",
  className = "",
}: SearchBoxProps) {
  return (
    <label className={`app-search ${className}`.trim()}>
      <span aria-hidden="true">
        <Icon name="search" size={17} />
      </span>
      <span className="visually-hidden">Search</span>
      <input
        className="app-search__input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
    </label>
  );
}
