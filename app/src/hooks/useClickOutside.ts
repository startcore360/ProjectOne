import { useEffect, type RefObject } from "react";

/**
 * Calls onClose when a pointer interaction happens outside the referenced element.
 */
export function useClickOutside<T extends Element>(
  ref: RefObject<T | null>,
  onClose: () => void,
): void {
  useEffect(() => {
    const handlePointerDown = (event: PointerEvent) => {
      const element = ref.current;
      const target = event.target;

      if (element && target instanceof Node && !element.contains(target)) {
        onClose();
      }
    };

    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [ref, onClose]);
}
