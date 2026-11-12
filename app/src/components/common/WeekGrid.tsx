type WeekSlot = { id: string; day: string; start: string; end: string; label: string };
type Props = { slots: WeekSlot[]; onSelect?: (slot: WeekSlot) => void };
export function WeekGrid({ slots, onSelect }: Props) {
  if (slots.length === 0) return <p>No timetable slots are available.</p>;
  return <div role="grid" aria-label="Weekly timetable">{slots.map((slot) => <button key={slot.id} type="button" role="gridcell" onClick={() => onSelect?.(slot)}>{slot.day} · {slot.start}–{slot.end}: {slot.label}</button>)}</div>;
}
