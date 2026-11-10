// If the user does nothing for this long, we treat them as "away" and log them out.
export const SESSION_IDLE_LIMIT_MS = 30 * 60 * 1000;

// Watches for any sign the user is still around (clicking, typing, scrolling, touching
// the screen) and keeps pushing the "you've been idle too long" timer back. If none of
// those happen for SESSION_IDLE_LIMIT_MS, it calls onIdle so the app can log them out.
export const startInactivityTracker = (onIdle: () => void, limitMs: number = SESSION_IDLE_LIMIT_MS) => {
  let timeoutId: number | undefined;

  const reset = () => {
    if (timeoutId) {
      window.clearTimeout(timeoutId);
    }

    timeoutId = window.setTimeout(onIdle, limitMs);
  };

  const events = ["mousedown", "mousemove", "keypress", "scroll", "touchstart"];

  events.forEach((event) => window.addEventListener(event, reset, { passive: true }));

  reset();

  return () => {
    events.forEach((event) => window.removeEventListener(event, reset));
    if (timeoutId) {
      window.clearTimeout(timeoutId);
    }
  };
};
