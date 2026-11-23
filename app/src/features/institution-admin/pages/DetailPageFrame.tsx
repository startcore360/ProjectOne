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
  const [state, setState] = useState<{
    data: DetailRecord | null;
    error: string;
    busy: boolean;
  }>({ data: null, error: "", busy: true });

  useEffect(() => {
    let active = true;

    Promise.resolve()
      .then(() => {
        if (active) setState({ data: null, error: "", busy: true });
        return load();
      })
      .then((record) => {
        if (active) setState({ data: record, error: "", busy: false });
      })
      .catch((cause: unknown) => {
        if (!active) return;
        setState({
          data: null,
          error: cause instanceof Error ? cause.message : "Unable to load this record.",
          busy: false,
        });
      });

    return () => {
      active = false;
    };
  }, [load]);

  const { data, error, busy } = state;

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
