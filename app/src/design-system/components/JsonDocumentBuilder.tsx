import { useId } from "react";
import Button from "./Button";
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
  while (record[`New field ${index}`] !== undefined) index += 1;
  return `New field ${index}`;
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

  const rename = (nextName: string) => {
    const normalized = nextName.trim();
    if (!onRename || !normalized || normalized === label) return;
    onRename(normalized);
  };

  return (
    <section className="eds-document-node" data-depth={Math.min(depth, 4)}>
      <div className="eds-document-node__header">
        {onRename ? (
          <TextField id={`document-key-${nodeId}`} label="Field name" defaultValue={label}
            onBlur={(event) => rename(event.currentTarget.value)} />
        ) : <strong className="eds-document-node__title">{label}</strong>}
        <SelectField id={`document-type-${nodeId}`} label="Value type" value={kind}
          onChange={(event) => updateKind(event.target.value as ValueKind)}>
          <option value="text">Text</option>
          <option value="number">Number</option>
          <option value="boolean">Yes / No</option>
          <option value="object">Group of fields</option>
          <option value="array">List</option>
          <option value="null">Not set</option>
        </SelectField>
        {onRemove && (
          <Button type="button" variant="tertiary" size="sm" className="eds-document-node__remove" onClick={onRemove}>
            Remove
          </Button>
        )}
      </div>

      {kind === "text" && (
        <TextField id={`document-value-${nodeId}`} label="Value" value={value as string}
          onChange={(event) => onChange(event.target.value)} />
      )}
      {kind === "number" && (
        <TextField id={`document-value-${nodeId}`} label="Value" type="number" value={String(value)}
          onChange={(event) => onChange(event.target.value === "" ? 0 : Number(event.target.value))} />
      )}
      {kind === "boolean" && (
        <SelectField id={`document-value-${nodeId}`} label="Value" value={String(value)}
          onChange={(event) => onChange(event.target.value === "true")}>
          <option value="true">Yes</option>
          <option value="false">No</option>
        </SelectField>
      )}
      {kind === "object" && (
        <div className="eds-document-node__children">
          {Object.entries(value as Record<string, DocumentValue>).map(([key, child]) => (
            <NodeEditor key={key} label={key} value={child}
              onChange={(next) => onChange({ ...(value as Record<string, DocumentValue>), [key]: next })}
              onRename={(nextName) => {
                const normalized = nextName.trim();
                const record = value as Record<string, DocumentValue>;
                if (!normalized || normalized === key || Object.prototype.hasOwnProperty.call(record, normalized)) return;
                const next = { ...record };
                delete next[key];
                next[normalized] = child;
                onChange(next);
              }}
              onRemove={() => {
                const next = { ...(value as Record<string, DocumentValue>) };
                delete next[key];
                onChange(next);
              }}
              depth={depth + 1} />
          ))}
          <Button type="button" variant="secondary" size="sm"
            onClick={() => onChange({ ...(value as Record<string, DocumentValue>), [nextKey(value as Record<string, DocumentValue>)]: "" })}>
            Add field
          </Button>
        </div>
      )}
      {kind === "array" && (
        <div className="eds-document-node__children">
          {(value as DocumentValue[]).map((child, index) => (
            <NodeEditor key={index} label={`Item ${index + 1}`} value={child}
              onChange={(next) => onChange((value as DocumentValue[]).map((item, itemIndex) => itemIndex === index ? next : item))}
              onRemove={() => onChange((value as DocumentValue[]).filter((_, itemIndex) => itemIndex !== index))}
              depth={depth + 1} />
          ))}
          <Button type="button" variant="secondary" size="sm" onClick={() => onChange([...(value as DocumentValue[]), ""])}>
            Add item
          </Button>
        </div>
      )}
      {kind === "null" && <p className="eds-document-node__empty">No value is currently set.</p>}
    </section>
  );
}

export interface JsonDocumentBuilderProps {
  value: Record<string, DocumentValue>;
  onChange: (value: Record<string, DocumentValue>) => void;
  title?: string;
  description?: string;
}

export function JsonDocumentBuilder({
  value,
  onChange,
  title = "Structured details",
  description = "Add details using the controls below. The application saves them in the required format.",
}: JsonDocumentBuilderProps) {
  return (
    <section className="eds-document-builder" aria-label={title}>
      <header className="eds-document-builder__intro">
        <h3>{title}</h3>
        <p>{description}</p>
      </header>
      <div className="eds-document-builder__fields">
        {Object.entries(value).map(([key, child]) => (
          <NodeEditor key={key} label={key} value={child}
            onChange={(next) => onChange({ ...value, [key]: next })}
            onRename={(nextName) => {
              const normalized = nextName.trim();
              if (!normalized || normalized === key || Object.prototype.hasOwnProperty.call(value, normalized)) return;
              const next = { ...value };
              delete next[key];
              next[normalized] = child;
              onChange(next);
            }}
            onRemove={() => {
              const next = { ...value };
              delete next[key];
              onChange(next);
            }} />
        ))}
      </div>
      <div className="eds-document-builder__actions">
        <Button type="button" variant="secondary" onClick={() => onChange({ ...value, [nextKey(value)]: "" })}>
          Add field
        </Button>
      </div>
    </section>
  );
}
