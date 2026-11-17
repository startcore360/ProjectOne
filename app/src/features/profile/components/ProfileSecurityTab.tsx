import { useNavigate } from "react-router-dom";
import { Badge, Button } from "../../../design-system/components";
import { BackupCodesSection } from "../../auth/components/BackupCodesSection";
import { CodesIcon, KeyIcon, ShieldIcon } from "./ProfileIcons";

export interface ProfileSecurityTabProps {
  isPlatform: boolean;
}

// AUTH_PASSWORD_CHANGE and MFA-01 (Scope: Self), gathered in one place. Two-step login is shown as
// "On" only for platform accounts: every platform role requires it (MfaRequirementService), while
// for tenant accounts /me/profile does not say whether it is set up, so nothing is claimed.
export function ProfileSecurityTab({ isPlatform }: ProfileSecurityTabProps) {
  const navigate = useNavigate();

  return (
    <section className="app-profile-card" aria-labelledby="profile-security-title">
      <header className="app-profile-card__header">
        <div>
          <h3 id="profile-security-title" className="app-profile-card__title">Sign-in and security</h3>
          <p className="app-profile-card__description">Keep your account safe and make sure you can always get back in.</p>
        </div>
      </header>

      <div className="app-profile-rows">
        <div className="app-profile-row">
          <span className="app-profile-row__icon"><KeyIcon /></span>
          <div className="app-profile-row__body">
            <h4 className="app-profile-row__title">Password</h4>
            <p className="app-profile-row__text">Use a strong password that you do not use anywhere else.</p>
          </div>
          <div className="app-profile-row__action">
            <Button variant="secondary" onClick={() => navigate("/change-password")}>
              Change password
            </Button>
          </div>
        </div>

        {isPlatform && (
          <div className="app-profile-row">
            <span className="app-profile-row__icon app-profile-row__icon--success"><ShieldIcon /></span>
            <div className="app-profile-row__body">
              <h4 className="app-profile-row__title">
                Two-step login <Badge tone="success">On</Badge>
              </h4>
              <p className="app-profile-row__text">
                Required for every platform role. You enter a code from your authenticator app each time you sign in.
              </p>
            </div>
          </div>
        )}

        <BackupCodesSection icon={<CodesIcon />} />
      </div>
    </section>
  );
}
