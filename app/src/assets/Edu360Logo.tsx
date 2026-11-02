import { useId, type CSSProperties, type SVGProps } from "react";

type LogoStyle = CSSProperties & {
  "--edu360-crescent-fill": string;
  "--edu360-word-fill": string;
};

export default function Edu360Logo(props: SVGProps<SVGSVGElement>) {
  const id = useId().replace(/:/g, "");
  const titleId = `${id}-logoTitle`;
  const descId = `${id}-logoDesc`;
  const greenCrescentId = `${id}-greenCrescent`;
  const wordGreenId = `${id}-wordGreen`;
  const logoStyle: LogoStyle = {
    "--edu360-crescent-fill": `url(#${greenCrescentId})`,
    "--edu360-word-fill": `url(#${wordGreenId})`,
  };

  return (
    <svg
      {...props}
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 536 465"
      role="img"
      aria-labelledby={`${titleId} ${descId}`}
      className={`edu360-logo${props.className ? ` ${props.className}` : ""}`}
      style={{ ...logoStyle, ...props.style }}
    >
      <title id={titleId}>EDU 360</title>
      <desc id={descId}>
        EDU 360 Education Management Platform logo. Open book, crescent, people,
        graduation cap and analytics.
      </desc>
      <defs>
        <linearGradient id={greenCrescentId} x1={0} y1={0} x2={1} y2={1}>
          <stop offset="0%" stopColor="var(--color-action-primary)" />
          <stop offset="55%" stopColor="var(--color-text-brand)" />
          <stop offset="100%" stopColor="var(--color-status-success-border)" />
        </linearGradient>
        <linearGradient id={`${id}-panelOne`} x1={0} y1={0} x2={1} y2={1}>
          <stop className="panel-one-start" offset="0%" stopColor="var(--color-action-primary)" />
          <stop
            className="panel-one-end"
            offset="100%"
            stopColor="var(--color-text-brand-strong)"
          />
        </linearGradient>
        <linearGradient id={`${id}-panelTwo`} x1={0} y1={0} x2={1} y2={1}>
          <stop className="panel-two-start" offset="0%" stopColor="var(--color-text-brand)" />
          <stop
            className="panel-two-end"
            offset="100%"
            stopColor="var(--color-action-primary)"
          />
        </linearGradient>
        <linearGradient id={`${id}-panelThree`} x1={0} y1={0} x2={1} y2={1}>
          <stop
            className="panel-three-start"
            offset="0%"
            stopColor="var(--color-status-success-border)"
          />
          <stop
            className="panel-three-end"
            offset="100%"
            stopColor="var(--color-text-brand)"
          />
        </linearGradient>
        <linearGradient id={wordGreenId} x1={0} y1={0} x2={1} y2={0}>
          <stop
            className="word-gradient-start"
            offset="0%"
            stopColor="var(--color-text-brand-strong)"
          />
          <stop
            className="word-gradient-mid"
            offset="62%"
            stopColor="var(--color-text-brand)"
          />
          <stop
            className="word-gradient-end"
            offset="100%"
            stopColor="var(--color-status-success-border)"
          />
        </linearGradient>
        <clipPath id={`${id}-people-panel-clip`} clipPathUnits="userSpaceOnUse">
          <path d="M164 194 Q164 181 177 174 L214 153 Q229 145 229 161 L229 255 Q207 255 190 270 L164 291 Z" />
        </clipPath>
        <clipPath id={`${id}-cap-panel-clip`} clipPathUnits="userSpaceOnUse">
          <path d="M235 151 Q235 139 247 132 L284 113 Q299 106 299 122 L299 257 Q277 263 263 282 L235 261 Z" />
        </clipPath>
        <clipPath id={`${id}-analytics-panel-clip`} clipPathUnits="userSpaceOnUse">
          <path d="M305 112 Q305 101 317 96 L351 81 Q366 75 366 91 L366 202 Q342 205 326 222 L305 239 Z" />
        </clipPath>
      </defs>
      <style>
        {`
          .crescent { fill: var(--edu360-crescent-fill); }
          .word-main { fill: var(--edu360-word-fill); }
          .tagline { fill: var(--color-text); }
          .accent-line { stroke: var(--color-status-success-border); }
          .pixel-dark { fill: var(--color-action-primary); }
          .pixel-mid { fill: var(--color-text-brand); }
          .pixel-light { fill: var(--color-status-success-border); }
          .book-page-base { fill: var(--edu360-crescent-fill); }
          .book-page-left { fill: var(--color-action-primary-hover); }
          .book-page-right { fill: var(--color-text-brand-strong); }
          .brand-type {
            font-family: Inter, Montserrat, Arial, sans-serif;
            font-weight: 800;
            letter-spacing: -2.4px;
          }
          .tag-type {
            font-family: Inter, Montserrat, Arial, sans-serif;
            font-size: 27px;
            font-weight: 500;
            letter-spacing: -0.45px;
          }
          html[data-theme="dark"] svg.edu360-logo .book-page {
            fill: var(--color-hero-text);
          }
          html[data-theme="dark"] svg.edu360-logo .crescent {
            fill: var(--color-text-brand);
          }
          html[data-theme="dark"] svg.edu360-logo .tagline {
            fill: var(--color-text-muted);
          }
          html[data-theme="dark"] svg.edu360-logo .accent-line {
            stroke: var(--color-text-brand);
          }
          html[data-theme="dark"] svg.edu360-logo .pixel-dark {
            fill: var(--color-text-brand-strong);
          }
          html[data-theme="dark"] svg.edu360-logo .pixel-mid {
            fill: var(--color-text-brand);
          }
          html[data-theme="dark"] svg.edu360-logo .pixel-light {
            fill: var(--color-text-brand-strong);
          }
          html[data-theme="dark"] svg.edu360-logo .people-icon,
          html[data-theme="dark"] svg.edu360-logo .cap-icon,
          html[data-theme="dark"] svg.edu360-logo .analytics-bars {
            fill: var(--color-text-brand-strong);
          }
          html[data-theme="dark"] svg.edu360-logo .analytics-line {
            stroke: var(--color-text-brand-strong);
          }
          html[data-theme="dark"] svg.edu360-logo .panel-one-start {
            stop-color: var(--color-hero-bg);
          }
          html[data-theme="dark"] svg.edu360-logo .panel-one-end {
            stop-color: var(--color-status-success-border);
          }
          html[data-theme="dark"] svg.edu360-logo .panel-two-start {
            stop-color: var(--color-bg-brand-soft);
          }
          html[data-theme="dark"] svg.edu360-logo .panel-two-end {
            stop-color: var(--color-hero-bg);
          }
          html[data-theme="dark"] svg.edu360-logo .panel-three-start {
            stop-color: var(--color-status-success-border);
          }
          html[data-theme="dark"] svg.edu360-logo .panel-three-end {
            stop-color: var(--color-bg-brand-soft);
          }
          html[data-theme="dark"] svg.edu360-logo .word-gradient-start {
            stop-color: var(--color-text-brand-strong);
          }
          html[data-theme="dark"] svg.edu360-logo .word-gradient-mid {
            stop-color: var(--color-text-brand);
          }
          html[data-theme="dark"] svg.edu360-logo .word-gradient-end {
            stop-color: var(--color-status-success-fg);
          }
        `}
      </style>
      <g className="emblem" transform="translate(0 10) scale(1 .78)">
        <path
          className="crescent"
          d="M359 42 C310 13 249 27 202 58 C142 98 105 159 104 218 C102 285 139 339 197 360 C246 378 307 366 359 337 C326 376 275 397 222 397 C137 397 75 335 75 247 C75 153 137 69 225 43 C276 28 325 29 359 42 Z"
        />
        <path
          d="M164 194 Q164 181 177 174 L214 153 Q229 145 229 161 L229 255 Q207 255 190 270 L164 291 Z"
          fill={`url(#${id}-panelOne)`}
        />
        <path
          d="M235 151 Q235 139 247 132 L284 113 Q299 106 299 122 L299 257 Q277 263 263 282 L235 261 Z"
          fill={`url(#${id}-panelTwo)`}
        />
        <path
          d="M305 112 Q305 101 317 96 L351 81 Q366 75 366 91 L366 202 Q342 205 326 222 L305 239 Z"
          fill={`url(#${id}-panelThree)`}
        />
        <g
          className="people-icon"
          fill="#FFFFFF"
          clipPath={`url(#${id}-people-panel-clip)`}
        >
          <circle cx={187} cy={185} r={5.2} />
          <circle cx={201} cy={181} r={5.8} />
          <circle cx={215} cy={185} r={5.2} />
          <path d="M177 207 Q178 193 188 193 Q198 193 199 207 Z" />
          <path d="M190 211 Q191 192 202 192 Q214 192 215 211 Z" />
          <path d="M211 207 Q212 193 222 193 Q232 193 233 207 Z" />
        </g>
        <g
          className="cap-icon"
          fill="#FFFFFF"
          clipPath={`url(#${id}-cap-panel-clip)`}
        >
          <path d="M243 174 L267 163 L291 174 L267 186 Z" />
          <path d="M250 180 L267 188 L284 180 L284 194 L267 202 L250 194 Z" />
          <path d="M289 176 L293 178 L293 194 L289 194 Z" />
        </g>
        <g
          className="analytics-line"
          fill="none"
          stroke="#FFFFFF"
          strokeWidth={4.2}
          strokeLinecap="round"
          strokeLinejoin="round"
          clipPath={`url(#${id}-analytics-panel-clip)`}
        >
          <path d="M319 158 L335 146 L345 150 L359 133" />
          <path d="M350 133 L360 132 L358 142" />
        </g>
        <g
          className="analytics-bars"
          fill="#FFFFFF"
          clipPath={`url(#${id}-analytics-panel-clip)`}
        >
          <rect x={318} y={168} width={8} height={18} />
          <rect x={334} y={158} width={8} height={28} />
          <rect x={350} y={148} width={8} height={38} />
        </g>
        <path
          className="book-page book-page-base"
          d="M151 292 C178 271 210 271 239 295 C249 303 257 314 263 324 C278 299 299 286 327 286 L397 286 C412 286 418 298 410 309 C405 316 396 320 385 320 L319 320 C294 320 276 331 263 349 C248 330 230 319 208 318 C187 317 168 323 150 333 C142 338 133 328 140 319 Z"
          fill={logoStyle["--edu360-crescent-fill"]}
        />
        <path
          className="book-page book-page-left"
          d="M144 340 C182 320 218 323 263 351 C225 338 190 338 155 355 C145 360 137 347 144 340 Z"
          fill="var(--color-action-primary-hover)"
        />
        <path
          className="book-page book-page-right"
          d="M263 351 C301 322 343 320 394 324 C345 330 302 337 263 365 C226 337 190 335 148 354 C190 343 226 343 263 351 Z"
          fill="var(--color-text-brand-strong)"
        />
        <rect className="pixel-dark" x={374} y={81} width={25} height={25} rx={6} />
        <rect className="pixel-dark" x={399} y={62} width={18} height={18} rx={5} />
        <rect className="pixel-mid" x={417} y={87} width={22} height={22} rx={6} />
        <rect className="pixel-light" x={395} y={111} width={22} height={22} rx={6} />
      </g>
      <text
        className="brand-type word-main"
        x={268}
        y={407}
        textAnchor="middle"
        fontSize={92}
      >
        EDU 360
      </text>
      <g className="tagline-group">
        <path
          className="accent-line"
          d="M21 438 H57 M479 438 H515"
          fill="none"
          strokeWidth={4}
          strokeLinecap="round"
        />
        <text
          className="tag-type tagline"
          x={268}
          y={447}
          textAnchor="middle"
        >
          Education Management Platform
        </text>
      </g>
    </svg>
  );
}
