type PrintableIdCardProps = {
  svg: string;
  title?: string;
};

export default function PrintableIdCard({ svg, title = "Printable identity card" }: PrintableIdCardProps) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-3">
      <h3 className="mb-2 text-sm font-semibold text-slate-800">{title}</h3>
      <iframe
        title={title}
        srcDoc={svg}
        sandbox=""
        className="h-[440px] w-full rounded-lg border border-slate-100 bg-slate-50"
      />
      <p className="mt-2 text-xs text-slate-500">The card preview is rendered in a sandboxed frame. Use the print control on the page to print the verified SVG.</p>
    </section>
  );
}
