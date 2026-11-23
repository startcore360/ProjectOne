import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";

type DetailRecord = Record<string, unknown>;

type DetailFrameProps = {
  title: string;
  backTo: string;
  backLabel: string;
  load: () => Promise<DetailRecord>;
  fields: string[];
};

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "object") return JSON.stringify(value, null, 2);
  return String(value);
}

export function DetailFrame({ title, backTo, backLabel, load, fields }: DetailFrameProps) {
  const [data, setData] = useState<DetailRecord | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    let active = true;
    setBusy(true);
    setError("");
    setData(null);

    load()
      .then((record) => {
        if (active) setData(record);
      })
      .catch((cause: unknown) => {
        if (!active) return;
        setError(cause instanceof Error ? cause.message : "Unable to load this record.");
      })
      .finally(() => {
        if (active) setBusy(false);
      });

    return () => {
      active = false;
    };
  }, [load]);

  return (
    <main className="academic-detail-page">
      <nav aria-label="Breadcrumb">
        <Link to={backTo}>{backLabel}</Link>
        <span aria-hidden="true"> / </span>
        <span>{title}</span>
      </nav>
      <h1>{title}</h1>
      {busy && <p role="status">Loading details…</p>}
      {error && <p role="alert">{error}</p>}
      {!busy && !error && data && (
        <dl>
          {fields.map((field) => (
            <React.Fragment key={field}>
              <dt>{field.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase())}</dt>
              <dd>{displayValue(data[field])}</dd>
            </React.Fragment>
          ))}
        </dl>
      )}
    </main>
  );
}
