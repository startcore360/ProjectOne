import { Link } from "react-router-dom";
import { Icon } from "../../../components/ui/Icon";
import type { RoleProfile } from "../roleDashboardCatalog";

export interface RoleCardProps {
  role: RoleProfile;
}

export function RoleCard({ role }: RoleCardProps) {
  return (
    <Link className="role-directory-card" to={`/role-preview/${role.code}`}>
      <span className="role-directory-card__level">{role.level}</span>
      <h3>{role.name}</h3>
      <p>{role.purpose}</p>
      <span className="role-directory-card__open">
        Open dashboard <Icon name="more" size={15} />
      </span>
    </Link>
  );
}
