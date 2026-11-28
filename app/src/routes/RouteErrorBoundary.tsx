import { Component, type ErrorInfo, type ReactNode } from "react";
import { ErrorState, Button } from "../design-system/components";

export interface RouteErrorBoundaryProps {
  children: ReactNode;
  fallbackMessage?: string;
  onReload?: () => void;
}

interface RouteErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

export class RouteErrorBoundary extends Component<
  RouteErrorBoundaryProps,
  RouteErrorBoundaryState
> {
  state: RouteErrorBoundaryState = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): RouteErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Route chunk loading error caught by boundary:", error, errorInfo);
  }

  handleReload = () => {
    if (this.props.onReload) {
      this.props.onReload();
    } else {
      window.location.reload();
    }
  };

  render() {
    if (this.state.hasError) {
      return (
        <div
          role="region"
          aria-label="Route loading error"
          style={{
            padding: "var(--space-6)",
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            minHeight: "200px",
            width: "100%",
          }}
        >
          <ErrorState
            title="Failed to load page"
            message={
              this.props.fallbackMessage ??
              "A newer version of the application may be available or your connection was interrupted. Please reload."
            }
            action={
              <Button variant="primary" onClick={this.handleReload}>
                Reload
              </Button>
            }
          />
        </div>
      );
    }
    return this.props.children;
  }
}

export default RouteErrorBoundary;
