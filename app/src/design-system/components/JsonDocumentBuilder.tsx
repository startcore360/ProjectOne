import { useId } from "react";
import { Button } from "./Button";
import TextField from "./TextField";
import SelectField from "./SelectField";

export type DocumentValue = string | number | boolean | null | DocumentValue[] | { [key: string]: DocumentValue };

type ValueKind = "text" | "number" | "boolean" | "object" | "array" | "null";

function isObject(value: DocumentValue): value is { [key: string]: DocumentValue } {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function kindOf(value: DocumentValue): ValueKind {
  if (value === null) return "null";
  if (Array.isArray(value)) return "array";
  if (isObject(value)) return "object";
  if (typeof value === "boolean") return "boolean";
  if (typeof value === "number") return "number";
  return "text";
}

function emptyValue(kind: ValueKind): DocumentValue {
  switch (kind) {
    case "number": return 0;
    case "boolean": return false;
    case "object": return {};
    case "array": return [];
    case "null": return null;
    default: return "";
  }
}

function nextKey(record: Record<string, DocumentValue>): string {
  let index = Object.keys(record).length + 1;
  while (record[`field${index}`] !== undefined) index += 1;
  return `field${index}`;
}

interface NodeEditorProps {
  label: string;
  value: DocumentValue;
  onChange: (value: DocumentValue) => void;
  onRemove?: () => void;
  onRename?: (name: string) => void;
  depth?: number;
}

function NodeEditor({ label, value, onChange, onRemove, onRename, depth = 0 }: NodeEditorProps) {
  const nodeId = useId();
  const kind = kindOf(value);
  const updateKind = (nextKind: ValueKind) => onChange(emptyValue(nextKind));


  return (
    <div style={{ border: "1px solid var(--border-subtle, #d7dee8)", borderRadius: "10px", padding: "0.75rem", marginTop: "0.6rem", marginLeft: depth ? "0.75rem" : 0 }}>
      <div style={{ display: "grid", gridTemplateColumns: onRename ? "minmax(100px, 1fr) minmax(120px, 0.8fr) auto" : "minmax(120px, 1fr) auto", gap: "0.5rem", alignItems: "end" }}>
        {onRename ? (
          <TextField id={`document-key-${nodeId}`} label="Field name" defaultValue={label}
            onBlur={(event) => onRename(event.currentTarget.value)} />
        ) : <strong style={{ paddingBottom: "0.6rem" }}>{label}</strong>}
        <SelectField id={`document-type-${nodeId}`} label="Value type" value={kind}
          onChange={(event) => updateKind(event.target.value as ValueKind)}>
          <option value="text">Text</option><option value="number">Number</option><option value="boolean">Yes / No</option>
          <option value="object">Group of fields</option><option value="array">List</option><option value="null">Not set</option>
        </SelectField>
        {onRemove && <Button type="button" variant="tertiary" size="sm" onClick={onRemove}>Remove</Button>}
      </div>

      {kind === "text" && <TextField id={`document-value-${nodeId}`} label="Value" value={value as string} onChange={(event) => onChange(event.target.value)} />}
      {kind === "number" && <TextField id={`document-value-${nodeId}`} label="Value" type="number" value={String(value)}
        onChange={(event) => onChange(event.target.value === "" ? 0 : Number(event.target.value))} />}
      {kind === "boolean" && <SelectField id={`document-value-${label.replace(/[^a-z0-9]/gi, "-")}`} label="Value" value={String(value)}
        onChange={(event) => onChange(event.target.value === "true")}><option value="true">Yes</option><option value="false">No</option></SelectField>}
      {kind === "object" && (
        <div style={{ marginTop: "0.4rem" }}>
          {Object.entries(value as Record<string, DocumentValue>).map(([key, child]) => (
            <NodeEditor key={key} label={key} value={child}
              onChange={(next) => onChange({ ...(value as Record<string, DocumentValue>), [key]: next })}
              onRename={(nextKeyName) => {
                const next = { ...(value as Record<string, DocumentValue>) };
                if (nextKeyName === key || !nextKeyName.trim()) return;
                const entries = Object.entries(next).filter(([entryKey]) => entryKey !== key);
                onChange(Object.fromEntries([...entries, [nextKeyName.trim(), child]]));
              }}
              onRemove={() => { const next = { ...(value as Record<string, DocumentValue>) }; delete next[key]; onChange(next); }}
              depth={depth + 1} />
          ))}
          <div style={{ marginTop: "0.6rem" }}>
            <Button type="button" variant="secondary" size="sm" onClick={() => onChange({ ...(value as Record<string, DocumentValue>), [nextKey(value as Record<string, DocumentValue>)]: "" })}>Add field</Button>
          </div>
        </div>
      )}
      {kind === "array" && (
        <div style={{ marginTop: "0.4rem" }}>
          {(value as DocumentValue[]).map((child, index) => (
            <NodeEditor key={index} label={`Item ${index + 1}`} value={child}
              onChange={(next) => onChange((value as DocumentValue[]).map((item, itemIndex) => itemIndex === index ? next : item))}
              onRemove={() => onChange((value as DocumentValue[]).filter((_, itemIndex) => itemIndex !== index))}
              depth={depth + 1} />
          ))}
          <div style={{ marginTop: "0.6rem" }}>
            <Button type="button" variant="secondary" size="sm" onClick={() => onChange([...(value as DocumentValue[]), ""])}>Add item</Button>
          </div>
        </div>
      )}
      {kind === "null" && <p style={{ color: "var(--text-muted)", margin: "0.4rem 0 0" }}>No value is currently set.</p>}
    </div>
  );
}

export interface JsonDocumentBuilderProps {
  value: Record<string, DocumentValue>;
  onChange: (value: Record<string, DocumentValue>) => void;
  title?: string;
  description?: string;
}

export function JsonDocumentBuilder({ value, onChange, title = "Structured details", description = "Add fields and choose the appropriate value type. The system stores these details automatically." }: JsonDocumentBuilderProps) {
  return (
    <section aria-label={title}>
      <div style={{ marginBottom: "0.75rem" }}>
        <strong>{title}</strong>
        <p style={{ color: "var(--text-secondary)", fontSize: "0.875rem", margin: "0.25rem 0 0" }}>{description}</p>
      </div>
      {Object.entries(value).map(([key, child]) => (
        <NodeEditor key={key} label={key} value={child}
          onChange={(next) => onChange({ ...value, [key]: next })}
          onRename={(nextKeyName) => {
            if (nextKeyName === key || !nextKeyName.trim()) return;
            const entries = Object.entries(value).filter(([entryKey]) => entryKey !== key);
            onChange(Object.fromEntries([...entries, [nextKeyName.trim(), child]]));
          }}
          onRemove={() => { const next = { ...value }; delete next[key]; onChange(next); }} />
      ))}
      <div style={{ marginTop: "0.75rem" }}>
        <Button type="button" variant="secondary" onClick={() => onChange({ ...value, [nextKey(value)]: "" })}>Add field</Button>
      </div>
    </section>
  );
}
