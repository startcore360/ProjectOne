import Edu360Logo from "../../assets/Edu360Logo";
import { cn } from "../../lib/utils";

export interface AppLoaderProps {
  /** Accessible label only - this is deliberately a light, branded loader, not a page-shaped
   * skeleton (see LazyRouteView in routes/Routes.tsx), so no visible text is shown. */
  label?: string;
  className?: string;
}

/**
 * The lighter branded loading state used wherever a lazy-loaded route's code is still being
 * fetched (routes/Routes.tsx's LazyRouteView) - a gently pulsing Edu360Logo mark instead of a
 * fixed-shape KPI+table skeleton that didn't resemble the real destination page. Mirrors the
 * first-load splash in index.html, which uses the same pulse animation on the full logo.
 */
export function AppLoader({ label = "Loading", className }: AppLoaderProps) {
  return (
    <div className={cn("eds-app-loader", className)} role="status" aria-busy="true" aria-label={label}>
      <Edu360Logo variant="mark" className="eds-app-loader__mark" />
    </div>
  );
}
export default AppLoader;
