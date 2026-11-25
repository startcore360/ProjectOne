export interface EstimateLineItem {
  description: string;
  quantity?: number;
  unitAmount?: number;
  amount: number;
}

export interface InstitutionalScope {
  planName?: string;
  universityType?: string;
  students?: number;
  colleges?: number;
  schools?: number;
  staff?: number;
  termMonths?: number;
}

export interface EstimateData {
  title: string;
  referenceNumber: string;
  date: string;
  dueDate?: string;
  validUntil?: string;
  period?: string;
  currencySymbol?: string;
  currencyCode?: string;
  billingCycle?: string;
  statusBadge?: string;
  lines: EstimateLineItem[];
  subtotal: number;
  taxPercent?: number;
  taxAmount?: number;
  discount?: number;
  total: number;
  notes?: string;

  // Institutional Scope Breakdown (Students, Colleges, Schools, Teachers)
  scope?: InstitutionalScope;

  // Dynamic "Who" - Recipient details
  institutionName?: string;
  recipientName?: string;
  contactPerson?: string;
  contactPhone?: string;
  contactEmail?: string;
  recipientGst?: string;
  recipientAddress?: string;

  // Issuer details
  issuerName?: string;
  issuerAddress?: string;
  issuerGst?: string;
  issuerPan?: string;
  issuerCin?: string;
  issuerSupport?: string;

  // Banking / UPI details
  bankName?: string;
  bankAccount?: string;
  bankIfsc?: string;
  upiId?: string;

  // Authorization
  preparedBy?: string;
  authorizedSignatory?: string;
}

export const DEFAULT_ISSUER = {
  name: "EDU 360 PLATFORM TECHNOLOGIES PVT. LTD.",
  tagline: "Academic Administration & Higher Education Cloud Infrastructure",
  address: "Tower 4, Silicon Tech Park, Outer Ring Road, Bengaluru, KA 560103",
  gst: "29AAACE3600Q1Z8",
  pan: "AAACE3600Q",
  cin: "U72200KA2026PTC360360",
  email: "billing@edu360.org",
  phone: "+91 80 4360 3600",
  bank: "HDFC Bank Ltd, Indiranagar Branch",
  account: "50200036036024",
  ifsc: "HDFC0001234",
  upi: "edu360billing@hdfcbank",
};

/**
 * Exact, authentic EDU 360 Logo SVG from Edu360Logo.tsx.
 * Renders the green crescent, 3 layered book panels, people/cap/analytics glyphs, and brand mark.
 */
export const EDU360_OFFICIAL_SVG = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="68 24 380 304" width="380" height="304">
  <defs>
    <linearGradient id="crescGrad" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#047857"/>
      <stop offset="55%" stop-color="#059669"/>
      <stop offset="100%" stop-color="#10b981"/>
    </linearGradient>
    <linearGradient id="p1" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#047857"/><stop offset="100%" stop-color="#064e3b"/></linearGradient>
    <linearGradient id="p2" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#059669"/><stop offset="100%" stop-color="#047857"/></linearGradient>
    <linearGradient id="p3" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#10b981"/><stop offset="100%" stop-color="#059669"/></linearGradient>
    <clipPath id="cp1"><path d="M164 194 Q164 181 177 174 L214 153 Q229 145 229 161 L229 255 Q207 255 190 270 L164 291 Z" /></clipPath>
    <clipPath id="cp2"><path d="M235 151 Q235 139 247 132 L284 113 Q299 106 299 122 L299 257 Q277 263 263 282 L235 261 Z" /></clipPath>
    <clipPath id="cp3"><path d="M305 112 Q305 101 317 96 L351 81 Q366 75 366 91 L366 202 Q342 205 326 222 L305 239 Z" /></clipPath>
  </defs>
  <g transform="translate(0 10) scale(1 .78)">
    <path fill="url(#crescGrad)" d="M359 42 C310 13 249 27 202 58 C142 98 105 159 104 218 C102 285 139 339 197 360 C246 378 307 366 359 337 C326 376 275 397 222 397 C137 397 75 335 75 247 C75 153 137 69 225 43 C276 28 325 29 359 42 Z"/>
    <path d="M164 194 Q164 181 177 174 L214 153 Q229 145 229 161 L229 255 Q207 255 190 270 L164 291 Z" fill="url(#p1)" />
    <path d="M235 151 Q235 139 247 132 L284 113 Q299 106 299 122 L299 257 Q277 263 263 282 L235 261 Z" fill="url(#p2)" />
    <path d="M305 112 Q305 101 317 96 L351 81 Q366 75 366 91 L366 202 Q342 205 326 222 L305 239 Z" fill="url(#p3)" />
    <g fill="#FFFFFF" clip-path="url(#cp1)">
      <circle cx="187" cy="185" r="5.2" />
      <circle cx="201" cy="181" r="5.8" />
      <circle cx="215" cy="185" r="5.2" />
      <path d="M177 207 Q178 193 188 193 Q198 193 199 207 Z" />
      <path d="M190 211 Q191 192 202 192 Q214 192 215 211 Z" />
      <path d="M211 207 Q212 193 222 193 Q232 193 233 207 Z" />
    </g>
    <g fill="#FFFFFF" clip-path="url(#cp2)">
      <path d="M243 174 L267 163 L291 174 L267 186 Z" />
      <path d="M250 180 L267 188 L284 180 L284 194 L267 202 L250 194 Z" />
      <path d="M289 176 L293 178 L293 194 L289 194 Z" />
    </g>
    <g fill="none" stroke="#FFFFFF" stroke-width="4.2" stroke-linecap="round" stroke-linejoin="round" clip-path="url(#cp3)">
      <path d="M319 158 L335 146 L345 150 L359 133" />
      <path d="M350 133 L360 132 L358 142" />
    </g>
    <g fill="#FFFFFF" clip-path="url(#cp3)">
      <rect x="318" y="168" width="8" height="18" />
      <rect x="334" y="158" width="8" height="28" />
      <rect x="350" y="148" width="8" height="38" />
    </g>
    <path d="M151 292 C178 271 210 271 239 295 C249 303 257 314 263 324 C278 299 299 286 327 286 L397 286 C412 286 418 298 410 309 C405 316 396 320 385 320 L319 320 C294 320 276 331 263 349 C248 330 230 319 208 318 C187 317 168 323 150 333 C142 338 133 328 140 319 Z" fill="#047857"/>
    <path d="M144 340 C182 320 218 323 263 351 C225 338 190 338 155 355 C145 360 137 347 144 340 Z" fill="#065f46"/>
    <path d="M263 351 C301 322 343 320 394 324 C345 330 302 337 263 365 C226 337 190 335 148 354 C190 343 226 343 263 351 Z" fill="#047857"/>
    <rect fill="#047857" x="374" y="81" width="25" height="25" rx="6" />
    <rect fill="#047857" x="399" y="62" width="18" height="18" rx="5" />
    <rect fill="#059669" x="417" y="87" width="22" height="22" rx="6" />
    <rect fill="#10b981" x="395" y="111" width="22" height="22" rx="6" />
  </g>
</svg>
`;

/**
 * Converts numeric amount to Indian currency words in ASCII text.
 */
export function numberToIndianWords(amount: number): string {
  const n = Math.round(Math.abs(amount));
  if (n === 0) return "Zero Rupees Only";

  const ones = [
    "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
    "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen",
  ];
  const tens = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];

  const formatSmall = (val: number): string => {
    let out = "";
    if (val >= 100) {
      out += `${ones[Math.floor(val / 100)]} Hundred `;
      val %= 100;
    }
    if (val >= 20) {
      out += `${tens[Math.floor(val / 10)]} `;
      val %= 10;
    }
    if (val > 0) {
      out += `${ones[val]} `;
    }
    return out.trim();
  };

  const cr = Math.floor(n / 10000000);
  const lk = Math.floor((n % 10000000) / 100000);
  const th = Math.floor((n % 100000) / 1000);
  const rem = n % 1000;

  const parts: string[] = [];
  if (cr > 0) parts.push(`${formatSmall(cr)} Crore`);
  if (lk > 0) parts.push(`${formatSmall(lk)} Lakh`);
  if (th > 0) parts.push(`${formatSmall(th)} Thousand`);
  if (rem > 0) parts.push(formatSmall(rem));

  return `${parts.join(" ").trim()} Rupees Only`;
}

/**
 * Formats a clean, high-impact text message for WhatsApp, Telegram, Email, and Clipboard.
 */
export function formatEstimateShareText(data: EstimateData): string {
  const sym = "Rs. ";
  const linesFormatted = data.lines
    .map((l, i) => {
      const qtyStr = l.quantity && l.quantity > 1 ? ` (x${l.quantity})` : "";
      return `${i + 1}. *${l.description}*${qtyStr} - ${sym}${Number(l.amount).toLocaleString("en-IN")}`;
    })
    .join("\n");

  const institution = data.institutionName || data.recipientName || "Valued Institution";
  const contact = [data.contactPerson, data.contactPhone].filter(Boolean).join(" - ");

  const scope = data.scope;
  const scopeLines = scope
    ? [
        `*ACADEMIC LICENSING & CAPACITY:*`,
        scope.planName ? `• Plan Tier: ${scope.planName}` : "",
        scope.students ? `• Students: ${scope.students.toLocaleString("en-IN")}` : "",
        scope.colleges ? `• Colleges: ${scope.colleges}` : "",
        scope.schools ? `• Schools: ${scope.schools}` : "",
        scope.staff ? `• Teachers & Faculty: ${scope.staff.toLocaleString("en-IN")}` : "",
        scope.termMonths ? `• Term Duration: ${scope.termMonths} Months` : "",
        "",
      ]
        .filter(Boolean)
        .join("\n")
    : "";

  return [
    `*EDU 360 · EDUCATION DRIVEN BY UNITY*`,
    `*TAX INVOICE & COMMERCIAL BILL*`,
    `=========================================`,
    `*Document:* ${data.title.toUpperCase()}`,
    `*Invoice / Ref #:* ${data.referenceNumber}`,
    `*Issued:* ${data.date}${data.dueDate ? `  |  *Due:* ${data.dueDate}` : ""}`,
    data.period ? `*Period:* ${data.period}` : "",
    "",
    `*BILLED TO:*`,
    `*${institution}*`,
    contact ? `Attn: ${contact}` : "",
    data.contactEmail ? `Email: ${data.contactEmail}` : "",
    data.recipientGst ? `GSTIN: ${data.recipientGst}` : "",
    "",
    scopeLines,
    `*ITEMIZED PARTICULARS:*`,
    linesFormatted,
    `-----------------------------------------`,
    data.discount && data.discount > 0
      ? `Subtotal: ${sym}${Number(data.subtotal).toLocaleString("en-IN")}\nDiscount: -${sym}${Number(data.discount).toLocaleString("en-IN")}`
      : "",
    `*TOTAL PAYABLE: ${sym}${Number(data.total).toLocaleString("en-IN")}*`,
    `_${numberToIndianWords(data.total)}_`,
    "",
    `*BANK TRANSFER / NEFT:*`,
    `Bank: ${data.bankName || DEFAULT_ISSUER.bank}`,
    `A/C: ${data.bankAccount || DEFAULT_ISSUER.account} | IFSC: ${data.bankIfsc || DEFAULT_ISSUER.ifsc}`,
    `UPI ID: ${data.upiId || DEFAULT_ISSUER.upi}`,
    "",
    data.notes ? `Notes: ${data.notes}\n` : "",
    `Issued by: ${DEFAULT_ISSUER.name}`,
    `Support: ${DEFAULT_ISSUER.email} | ${DEFAULT_ISSUER.phone}`,
  ]
    .filter(Boolean)
    .join("\n");
}

function loadEdu360LogoImage(): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error("Could not load EDU 360 logo image"));
    img.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(EDU360_OFFICIAL_SVG);
  });
}

/**
 * Master Canvas Renderer: Draws the entire invoice in full A4 high-DPI resolution
 * with the official EDU 360 logo, institutional metrics, itemized table, and corporate branding.
 */
export async function renderInvoiceCanvas(data: EstimateData): Promise<HTMLCanvasElement> {
  // A4 proportion: 1240 x 1754 (approx 150 DPI)
  const width = 1240;
  const height = 1754;
  const padding = 56;
  const contentWidth = width - padding * 2;

  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Could not initialize 2D canvas context");

  const curPrefix = "INR ";
  const institution = data.institutionName || data.recipientName || "Valued Academic Institution";

  // Crisp White Document background
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, width, height);

  // Modern Top Accent Stripe
  const grad = ctx.createLinearGradient(0, 0, width, 0);
  grad.addColorStop(0, "#047857");
  grad.addColorStop(0.5, "#10b981");
  grad.addColorStop(1, "#34d399");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, width, 8);

  // Outer Border
  ctx.strokeStyle = "#e2e8f0";
  ctx.lineWidth = 1.5;
  ctx.strokeRect(16, 16, width - 32, height - 32);

  // 1. Header Section: Logo + Corporate info on left, Invoice metadata on right
  const headerY = 48;
  const logoX = padding;
  const logoY = headerY;

  // Load and draw the official EDU 360 Logo
  try {
    const logoImg = await loadEdu360LogoImage();
    ctx.drawImage(logoImg, logoX, logoY, 88, 70);
  } catch {
    // Fallback if image load blocked
    ctx.fillStyle = "#059669";
    ctx.beginPath();
    ctx.arc(logoX + 36, logoY + 35, 30, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 20px system-ui, sans-serif";
    ctx.fillText("360", logoX + 18, logoY + 42);
  }

  // Brand Typography
  const brandTextX = logoX + 104;
  ctx.fillStyle = "#0f172a";
  ctx.font = "bold 28px system-ui, sans-serif";
  ctx.fillText("EDU 360", brandTextX, logoY + 30);

  ctx.fillStyle = "#059669";
  ctx.font = "bold 11px system-ui, sans-serif";
  ctx.fillText("EDUCATION DRIVEN BY UNITY", brandTextX, logoY + 48);

  ctx.fillStyle = "#64748b";
  ctx.font = "12px system-ui, sans-serif";
  ctx.fillText("Higher Education Academic Administration & Governance Cloud", brandTextX, logoY + 66);

  // Metadata block on right
  const metaRightX = width - padding;
  // Pill Badge for status
  ctx.fillStyle = "#ecfdf5";
  ctx.strokeStyle = "#a7f3d0";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.roundRect(metaRightX - 180, logoY + 2, 180, 28, 14);
  ctx.fill();
  ctx.stroke();

  ctx.fillStyle = "#047857";
  ctx.font = "bold 12px system-ui, sans-serif";
  ctx.textAlign = "center";
  ctx.fillText(data.statusBadge || "TAX INVOICE - ORIGINAL", metaRightX - 90, logoY + 20);
  ctx.textAlign = "left";

  ctx.textAlign = "right";
  ctx.fillStyle = "#0f172a";
  ctx.font = "bold 16px system-ui, sans-serif";
  ctx.fillText(`Invoice / Ref: #${data.referenceNumber}`, metaRightX, logoY + 54);

  ctx.fillStyle = "#64748b";
  ctx.font = "13px system-ui, sans-serif";
  ctx.fillText(`Issued: ${data.date}`, metaRightX, logoY + 72);

  if (data.dueDate || data.validUntil) {
    ctx.fillStyle = "#059669";
    ctx.font = "bold 13px system-ui, sans-serif";
    ctx.fillText(`Due Date: ${data.dueDate || data.validUntil}`, metaRightX, logoY + 90);
  }
  ctx.textAlign = "left";

  // Divider
  ctx.strokeStyle = "#e2e8f0";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(padding, 164);
  ctx.lineTo(width - padding, 164);
  ctx.stroke();

  // 2. Two Modern Cards: ISSUED BY & BILLED TO
  const cardsY = 184;
  const cardW = (contentWidth - 24) / 2;
  const cardH = 136;

  // Box 1: ISSUED BY / SUPPLIER
  ctx.fillStyle = "#f8fafc";
  ctx.strokeStyle = "#e2e8f0";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.roundRect(padding, cardsY, cardW, cardH, 12);
  ctx.fill();
  ctx.stroke();

  ctx.fillStyle = "#059669";
  ctx.font = "bold 11px system-ui, sans-serif";
  ctx.fillText("ISSUED BY / SUPPLIER", padding + 18, cardsY + 26);

  ctx.fillStyle = "#1e293b";
  ctx.font = "bold 14px system-ui, sans-serif";
  ctx.fillText(DEFAULT_ISSUER.name, padding + 18, cardsY + 50);

  ctx.fillStyle = "#64748b";
  ctx.font = "12px system-ui, sans-serif";
  ctx.fillText(`GSTIN: ${DEFAULT_ISSUER.gst}   |   PAN: ${DEFAULT_ISSUER.pan}`, padding + 18, cardsY + 72);
  ctx.fillText(DEFAULT_ISSUER.address, padding + 18, cardsY + 92);
  ctx.fillText(`Email: ${DEFAULT_ISSUER.email}   |   Phone: ${DEFAULT_ISSUER.phone}`, padding + 18, cardsY + 114);

  // Box 2: BILLED TO / RECIPIENT
  const toCardX = padding + cardW + 24;
  ctx.fillStyle = "#f0fdf4";
  ctx.strokeStyle = "#bbf7d0";
  ctx.beginPath();
  ctx.roundRect(toCardX, cardsY, cardW, cardH, 12);
  ctx.fill();
  ctx.stroke();

  ctx.fillStyle = "#059669";
  ctx.font = "bold 11px system-ui, sans-serif";
  ctx.fillText("BILLED TO / RECIPIENT", toCardX + 18, cardsY + 26);

  ctx.fillStyle = "#065f46";
  ctx.font = "bold 18px system-ui, sans-serif";
  ctx.fillText(institution, toCardX + 18, cardsY + 52);

  ctx.fillStyle = "#334155";
  ctx.font = "13px system-ui, sans-serif";
  const contactLine = [data.contactPerson, data.contactPhone].filter(Boolean).join("   •   ");
  ctx.fillText(contactLine || "Authorized Institutional Representative", toCardX + 18, cardsY + 76);

  ctx.fillStyle = "#64748b";
  ctx.font = "12px system-ui, sans-serif";
  ctx.fillText([data.contactEmail, data.recipientGst ? `GSTIN: ${data.recipientGst}` : ""].filter(Boolean).join("   |   "), toCardX + 18, cardsY + 98);
  if (data.recipientAddress) {
    ctx.fillText(data.recipientAddress, toCardX + 18, cardsY + 118);
  }

  // 3. Institutional Capacity & Scope Card (Students, Colleges, Schools, Teachers)
  const scopeY = cardsY + cardH + 20;
  const scopeH = 140;

  // Outer Card
  ctx.fillStyle = "#ffffff";
  ctx.strokeStyle = "#10b981";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.roundRect(padding, scopeY, contentWidth, scopeH, 12);
  ctx.fill();
  ctx.stroke();

  // Header Banner Strip
  ctx.fillStyle = "#f0fdf4";
  ctx.beginPath();
  ctx.roundRect(padding, scopeY, contentWidth, 34, [12, 12, 0, 0]);
  ctx.fill();

  ctx.fillStyle = "#047857";
  ctx.font = "bold 11px system-ui, sans-serif";
  ctx.fillText("ACADEMIC LICENSING CAPACITY & INSTITUTIONAL SCOPE", padding + 18, scopeY + 22);

  const scope = data.scope;
  const planLabel = scope?.planName || "Enterprise Academic Cloud Tier";
  const studentsCount = scope?.students ? `${scope.students.toLocaleString("en-IN")} Students` : "Standard Tier";
  const collegesCount = scope?.colleges ? `${scope.colleges} ${scope.colleges === 1 ? "Campus" : "Campuses"}` : "1 Campus";
  const schoolsCount = scope?.schools ? `${scope.schools} ${scope.schools === 1 ? "School/Dept" : "Schools/Depts"}` : "Standard Units";
  const staffCount = scope?.staff ? `${scope.staff.toLocaleString("en-IN")} Faculty` : "Full Access";
  const termLabel = scope?.termMonths ? `${scope.termMonths} Months Subscription Term` : "Annual Subscription Term";

  // Term duration in header (right-aligned)
  ctx.textAlign = "right";
  ctx.fillStyle = "#059669";
  ctx.font = "bold 11px system-ui, sans-serif";
  ctx.fillText(termLabel.toUpperCase(), width - padding - 18, scopeY + 22);
  ctx.textAlign = "left";

  // Tier 1: Plan Tier & Edition (Dedicated full wide row - zero chance of overlap)
  ctx.fillStyle = "#64748b";
  ctx.font = "bold 9.5px system-ui, sans-serif";
  ctx.fillText("LICENSED PLAN TIER & EDITION", padding + 18, scopeY + 52);

  ctx.fillStyle = "#0f172a";
  ctx.font = "bold 15px system-ui, sans-serif";
  ctx.fillText(planLabel, padding + 18, scopeY + 72, contentWidth - 36);

  // Divider between Plan Tier and the 4 metric cards
  ctx.strokeStyle = "#e2e8f0";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(padding + 14, scopeY + 84);
  ctx.lineTo(width - padding - 14, scopeY + 84);
  ctx.stroke();

  // Tier 2: 4-Column Grid for Institutional Dimensions (Each gets 282px width)
  const metrics = [
    { label: "STUDENTS ENROLLED", value: studentsCount },
    { label: "COLLEGES / CAMPUSES", value: collegesCount },
    { label: "SCHOOLS & DEPTS", value: schoolsCount },
    { label: "FACULTY & STAFF", value: staffCount },
  ];

  const colWidth = contentWidth / metrics.length;
  metrics.forEach((m, idx) => {
    const mx = padding + idx * colWidth;
    ctx.fillStyle = "#64748b";
    ctx.font = "bold 10px system-ui, sans-serif";
    ctx.fillText(m.label, mx + 18, scopeY + 104);

    ctx.fillStyle = "#047857";
    ctx.font = "bold 15px system-ui, sans-serif";
    ctx.fillText(m.value, mx + 18, scopeY + 126, colWidth - 28);

    if (idx < metrics.length - 1) {
      ctx.strokeStyle = "#f1f5f9";
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(mx + colWidth, scopeY + 92);
      ctx.lineTo(mx + colWidth, scopeY + 132);
      ctx.stroke();
    }
  });

  // 4. Itemized Particulars Table
  const tableY = scopeY + scopeH + 24;
  const headerHeight = 44;

  ctx.fillStyle = "#0f172a";
  ctx.beginPath();
  ctx.roundRect(padding, tableY, contentWidth, headerHeight, [8, 8, 0, 0]);
  ctx.fill();

  ctx.fillStyle = "#ffffff";
  ctx.font = "bold 13px system-ui, sans-serif";
  ctx.fillText("#", padding + 20, tableY + 28);
  ctx.fillText("ITEM / SERVICE PARTICULARS", padding + 60, tableY + 28);
  ctx.fillText("SAC CODE", padding + 680, tableY + 28);
  ctx.fillText("QTY", padding + 800, tableY + 28);
  ctx.fillText("UNIT RATE", padding + 890, tableY + 28);
  ctx.textAlign = "right";
  ctx.fillText("AMOUNT", width - padding - 24, tableY + 28);
  ctx.textAlign = "left";

  let rowY = tableY + headerHeight;
  const rowHeight = 48;
  data.lines.forEach((line, idx) => {
    ctx.fillStyle = idx % 2 === 1 ? "#f8fafc" : "#ffffff";
    ctx.fillRect(padding, rowY, contentWidth, rowHeight);
    ctx.strokeStyle = "#e2e8f0";
    ctx.lineWidth = 1;
    ctx.strokeRect(padding, rowY, contentWidth, rowHeight);

    ctx.fillStyle = "#64748b";
    ctx.font = "13px system-ui, sans-serif";
    ctx.fillText(String(idx + 1), padding + 20, rowY + 30);

    ctx.fillStyle = "#0f172a";
    ctx.font = "500 14px system-ui, sans-serif";
    ctx.fillText(line.description, padding + 60, rowY + 30, 600);

    ctx.fillStyle = "#64748b";
    ctx.fillText("998314", padding + 680, rowY + 30);
    ctx.fillText(String(line.quantity || 1), padding + 800, rowY + 30);
    ctx.fillText(line.unitAmount ? `${curPrefix}${Number(line.unitAmount).toLocaleString("en-IN")}` : "-", padding + 890, rowY + 30);

    ctx.textAlign = "right";
    ctx.fillStyle = "#0f172a";
    ctx.font = "bold 14px system-ui, sans-serif";
    ctx.fillText(`${curPrefix}${Number(line.amount).toLocaleString("en-IN")}`, width - padding - 24, rowY + 30);
    ctx.textAlign = "left";

    rowY += rowHeight;
  });

  // 5. Financial Summary & Wire Remittance Box
  const summaryY = rowY + 28;
  const summaryBoxH = 120;

  // Left Bank Box
  ctx.fillStyle = "#f8fafc";
  ctx.strokeStyle = "#e2e8f0";
  ctx.beginPath();
  ctx.roundRect(padding, summaryY, cardW, summaryBoxH, 12);
  ctx.fill();
  ctx.stroke();

  ctx.fillStyle = "#0369a1";
  ctx.font = "bold 12px system-ui, sans-serif";
  ctx.fillText("PAYMENT & WIRE REMITTANCE DETAILS", padding + 20, summaryY + 28);

  ctx.fillStyle = "#334155";
  ctx.font = "13px system-ui, sans-serif";
  ctx.fillText(`Bank: ${data.bankName || DEFAULT_ISSUER.bank}`, padding + 20, summaryY + 52);
  ctx.font = "bold 13px system-ui, sans-serif";
  ctx.fillText(`A/C: ${data.bankAccount || DEFAULT_ISSUER.account}   |   IFSC: ${data.bankIfsc || DEFAULT_ISSUER.ifsc}`, padding + 20, summaryY + 76);
  ctx.fillStyle = "#059669";
  ctx.fillText(`UPI Payment ID: ${data.upiId || DEFAULT_ISSUER.upi}`, padding + 20, summaryY + 100);

  // Right Totals Box
  ctx.fillStyle = "#f0fdf4";
  ctx.strokeStyle = "#86efac";
  ctx.beginPath();
  ctx.roundRect(toCardX, summaryY, cardW, summaryBoxH, 12);
  ctx.fill();
  ctx.stroke();

  ctx.fillStyle = "#065f46";
  ctx.font = "14px system-ui, sans-serif";
  ctx.fillText(`Subtotal: ${curPrefix}${Number(data.subtotal).toLocaleString("en-IN")}`, toCardX + 24, summaryY + 34);

  if (data.discount && data.discount > 0) {
    ctx.fillStyle = "#dc2626";
    ctx.fillText(`Institutional Rebate: -${curPrefix}${Number(data.discount).toLocaleString("en-IN")}`, toCardX + 24, summaryY + 58);
  }

  // Highlight Total Banner
  ctx.fillStyle = "#047857";
  ctx.beginPath();
  ctx.roundRect(toCardX + 16, summaryY + 70, cardW - 32, 38, 8);
  ctx.fill();

  ctx.fillStyle = "#ffffff";
  ctx.font = "bold 16px system-ui, sans-serif";
  ctx.fillText("TOTAL PAYABLE:", toCardX + 30, summaryY + 94);

  ctx.textAlign = "right";
  ctx.font = "bold 20px system-ui, sans-serif";
  ctx.fillText(`${curPrefix}${Number(data.total).toLocaleString("en-IN")}`, toCardX + cardW - 32, summaryY + 95);
  ctx.textAlign = "left";

  // 6. Amount in Words Banner
  const wordsY = summaryY + summaryBoxH + 20;
  ctx.fillStyle = "#f8fafc";
  ctx.strokeStyle = "#e2e8f0";
  ctx.beginPath();
  ctx.roundRect(padding, wordsY, contentWidth, 36, 8);
  ctx.fill();
  ctx.stroke();

  ctx.fillStyle = "#0f172a";
  ctx.font = "bold 13px system-ui, sans-serif";
  ctx.fillText(`Amount in Words: ${numberToIndianWords(data.total)}`, padding + 18, wordsY + 23);

  // 7. Terms & Digital Authorization Seal
  const footerAreaY = wordsY + 54;

  ctx.fillStyle = "#64748b";
  ctx.font = "bold 12px system-ui, sans-serif";
  ctx.fillText("Terms & Statutory Conditions:", padding, footerAreaY + 16);
  ctx.font = "12px system-ui, sans-serif";
  ctx.fillText("1. Payment is due within stated validity period. Service delivered via multi-tenant cloud isolation.", padding, footerAreaY + 36);
  ctx.fillText("2. Subject to Bengaluru, Karnataka state jurisdiction. Formal dispute resolution via institutional terms.", padding, footerAreaY + 54);

  // Digital Seal Stamp
  const sealW = 260;
  const sealX = width - padding - sealW;
  ctx.fillStyle = "#f0fdf4";
  ctx.strokeStyle = "#059669";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.roundRect(sealX, footerAreaY, sealW, 64, 8);
  ctx.fill();
  ctx.stroke();

  ctx.fillStyle = "#059669";
  ctx.font = "bold 12px system-ui, sans-serif";
  ctx.textAlign = "center";
  ctx.fillText("DIGITALLY AUTHORIZED & SEALED", sealX + sealW / 2, footerAreaY + 26);
  ctx.fillStyle = "#0f172a";
  ctx.font = "11px system-ui, sans-serif";
  ctx.fillText(data.preparedBy || "Commercial Operations Desk", sealX + sealW / 2, footerAreaY + 44);
  ctx.textAlign = "left";

  // 8. Fixed Bottom Corporate Footer
  const btmY = height - 42;
  ctx.strokeStyle = "#e2e8f0";
  ctx.beginPath();
  ctx.moveTo(padding, btmY - 14);
  ctx.lineTo(width - padding, btmY - 14);
  ctx.stroke();

  ctx.fillStyle = "#94a3b8";
  ctx.font = "11px system-ui, sans-serif";
  ctx.fillText(
    `EDU 360 Platform Technologies Pvt Ltd   |   CIN: ${DEFAULT_ISSUER.cin}   |   GSTIN: ${DEFAULT_ISSUER.gst}   |   ISO 27001 Multi-Tenant Isolation`,
    padding,
    btmY
  );
  ctx.textAlign = "right";
  ctx.fillText("Official Computer-Generated Tax Document", width - padding, btmY);
  ctx.textAlign = "left";

  return canvas;
}

/**
 * Builds a valid PDF 1.4 binary stream embedding the high-resolution master canvas image.
 */
function createPdfFromJpeg(jpegBytes: Uint8Array, imgW: number, imgH: number): Blob {
  const ptWidth = 595.28;
  const ptHeight = 841.89;

  const enc = new TextEncoder();
  const parts: Uint8Array[] = [];
  let currentOffset = 0;
  const offsets: number[] = [];

  const addStr = (s: string) => {
    const b = enc.encode(s);
    parts.push(b);
    currentOffset += b.length;
  };

  const addBytes = (b: Uint8Array) => {
    parts.push(b);
    currentOffset += b.length;
  };

  // Header
  addStr("%PDF-1.4\n");

  // 1: Catalog
  offsets.push(currentOffset);
  addStr("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

  // 2: Pages
  offsets.push(currentOffset);
  addStr("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

  // 3: Page
  offsets.push(currentOffset);
  addStr(
    `3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${ptWidth} ${ptHeight}] /Resources << /XObject << /Im1 5 0 R >> >> /Contents 4 0 R >>\nendobj\n`
  );

  // 4: Contents Stream
  const streamData = `q ${ptWidth.toFixed(2)} 0 0 ${ptHeight.toFixed(2)} 0 0 cm /Im1 Do Q`;
  offsets.push(currentOffset);
  addStr(`4 0 obj\n<< /Length ${streamData.length} >>\nstream\n${streamData}\nendstream\nendobj\n`);

  // 5: Image XObject
  offsets.push(currentOffset);
  addStr(
    `5 0 obj\n<< /Type /XObject /Subtype /Image /Width ${imgW} /Height ${imgH} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.length} >>\nstream\n`
  );
  addBytes(jpegBytes);
  addStr("\nendstream\nendobj\n");

  // Xref table
  const xrefOffset = currentOffset;
  let xref = `xref\n0 6\n0000000000 65535 f \n`;
  for (const off of offsets) {
    xref += off.toString().padStart(10, "0") + " 00000 n \n";
  }
  xref += `trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
  addStr(xref);

  const totalLength = parts.reduce((acc, p) => acc + p.length, 0);
  const fullBytes = new Uint8Array(totalLength);
  let pos = 0;
  for (const p of parts) {
    fullBytes.set(p, pos);
    pos += p.length;
  }
  return new Blob([fullBytes.buffer as ArrayBuffer], { type: "application/pdf" });
}

/**
 * Generates an executive Vector PDF matching the master canvas design with 100% precision.
 */
export async function generateEstimatePdfBlob(data: EstimateData): Promise<Blob> {
  const canvas = await renderInvoiceCanvas(data);
  const dataUrl = canvas.toDataURL("image/jpeg", 0.95);
  const base64 = dataUrl.split(",")[1];
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return createPdfFromJpeg(bytes, canvas.width, canvas.height);
}

/**
 * Generates an ultra-crisp PNG invoice card from the master canvas.
 */
export async function generateEstimateImageBlob(data: EstimateData): Promise<Blob> {
  const canvas = await renderInvoiceCanvas(data);
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error("Failed to export image blob"));
    }, "image/png");
  });
}
