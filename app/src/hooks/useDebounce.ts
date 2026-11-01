import { useEffect, useState } from "react";

/**
 * Returns the latest value after it has remained unchanged for the given delay.
 */
export function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timeoutId = window.setTimeout(
      () => {
        setDebouncedValue(value);
      },
      Math.max(0, delay),
    );

    return () => window.clearTimeout(timeoutId);
  }, [value, delay]);

  return debouncedValue;
}
