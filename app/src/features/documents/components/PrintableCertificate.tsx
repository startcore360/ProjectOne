import { useRef } from "react";
import Button from "../../../design-system/components/Button";

type PrintableCertificateProps = { html: string; title?: string };

export default function PrintableCertificate({ html, title = "Official certificate" }: PrintableCertificateProps) {
  const frame = useRef<HTMLIFrameElement>(null);
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-3">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-slate-800">{title}</h3>
        <Button size="sm" variant="secondary" onClick={() => frame.current?.contentWindow?.print()}>Print certificate</Button>
      </div>
      <iframe ref={frame} title={title} srcDoc={html} sandbox="allow-same-origin"
        className="h-[620px] w-full rounded-lg border border-slate-200 bg-white" />
    </section>
  );
}
