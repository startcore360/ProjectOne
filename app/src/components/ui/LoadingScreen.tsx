import Edu360Logo from "../../assets/Edu360Logo";

export interface LoadingScreenProps {
  label?: string;
}

export function LoadingScreen({
  label = "Loading EDU 360...",
}: LoadingScreenProps) {
  return (
    <div
      className="initial-loader-wrapper"
      role="status"
      aria-label={label}
    >
      <div className="initial-loader-content">
        <Edu360Logo className="edu360-initial-logo" />
        <div className="initial-loader-spinner-bar">
          <div className="initial-loader-spinner" aria-hidden="true" />
          <div className="initial-loader-label">{label}</div>
        </div>
      </div>
    </div>
  );
}

export default LoadingScreen;
