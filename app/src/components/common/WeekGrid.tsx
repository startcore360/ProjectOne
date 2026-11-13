import { useRef, useState } from "react";

type WeekSlot = { id: string; day: string; start: string; end: string; label: string };
type Props = { slots: WeekSlot[]; onSelect?: (slot: WeekSlot) => void };

export function WeekGrid({ slots, onSelect }: Props) {
  const [activeIndex, setActiveIndex] = useState(0);
  const buttons = useRef<Array<HTMLButtonElement | null>>([]);

  if (slots.length === 0) return <p>No timetable slots are available.</p>;

  const focusSlot = (index: number) => {
    const next = (index + slots.length) % slots.length;
    setActiveIndex(next);
    buttons.current[next]?.focus();
  };

  return (
    <div role="grid" aria-label="Weekly timetable" aria-rowcount={slots.length} aria-colcount={1} className="week-grid">
      {slots.map((slot, index) => (
        <div role="row" key={slot.id} className="week-grid__row">
          <div role="gridcell">
            <button
              ref={(node) => { buttons.current[index] = node; }}
              type="button"
              tabIndex={index === activeIndex ? 0 : -1}
              aria-label={`${slot.day}, ${slot.start} to ${slot.end}: ${slot.label}`}
              onFocus={() => setActiveIndex(index)}
              onClick={() => onSelect?.(slot)}
              onKeyDown={(event) => {
                if (event.key === "ArrowDown" || event.key === "ArrowRight") {
                  event.preventDefault();
                  focusSlot(index + 1);
                } else if (event.key === "ArrowUp" || event.key === "ArrowLeft") {
                  event.preventDefault();
                  focusSlot(index - 1);
                } else if (event.key === "Home") {
                  event.preventDefault();
                  focusSlot(0);
                } else if (event.key === "End") {
                  event.preventDefault();
                  focusSlot(slots.length - 1);
                }
              }}
            >
              {slot.day} · {slot.start}–{slot.end}: {slot.label}
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
