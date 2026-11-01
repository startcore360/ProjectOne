export const SESSION_IDLE_LIMIT_MS = 30 * 60 * 1000;

export const startInactivityTracker = (onIdle: () => void) => {
  let timeoutId: number | undefined;

  const reset = () => {
    if (timeoutId) {
      window.clearTimeout(timeoutId);
    }

    timeoutId = window.setTimeout(onIdle, SESSION_IDLE_LIMIT_MS);
  };

  const events = ["mousedown", "mousemove", "keypress", "scroll", "touchstart"];

  events.forEach((event) =>
    window.addEventListener(event, reset, { passive: true }),
  );

  reset();

  return () => {
    events.forEach((event) => window.removeEventListener(event, reset));
    if (timeoutId) {
      window.clearTimeout(timeoutId);
    }
  };
};
