import React, { useEffect, useRef, useState } from 'react';
import { motion, animate } from 'framer-motion';

export interface PerfMetrics {
  fps: number;
  low: number;
  ping: number;
}

interface Props {
  score: number; // 0..1 — degree of enabled optimization
  metrics: PerfMetrics;
  locale: 'en' | 'zh';
}

// Count-up animated integer.
const AnimatedNumber: React.FC<{ value: number }> = ({ value }) => {
  const [display, setDisplay] = useState(value);
  const prev = useRef(value);
  useEffect(() => {
    const controls = animate(prev.current, value, {
      duration: 0.5,
      ease: 'easeOut',
      onUpdate: (v) => setDisplay(Math.round(v)),
    });
    prev.current = value;
    return () => controls.stop();
  }, [value]);
  return <>{display}</>;
};

// A speedometer-style gauge: red → green → gold arc with a needle at the optimization degree, plus live
// FPS / low / ping readouts. The needle endpoint is computed with trig (no SVG transform-origin quirks).
export const PerformanceGauge: React.FC<Props> = ({ score, metrics, locale }) => {
  const s = Math.max(0, Math.min(1, score));
  const pct = Math.round(s * 100);
  const t = (zh: string, en: string) => (locale === 'en' ? en : zh);

  // score 0 -> 180° (left), 0.5 -> 90° (up), 1 -> 0° (right). SVG y is down, so subtract sin.
  const rad = ((180 - s * 180) * Math.PI) / 180;
  const len = 64;
  const tipX = 100 + len * Math.cos(rad);
  const tipY = 100 - len * Math.sin(rad);

  const chips = [
    { label: 'FPS', raw: metrics.fps, suffix: '' },
    { label: t('LOW', 'LOW'), raw: metrics.low, suffix: '' },
    { label: t('延迟', 'PING'), raw: metrics.ping, suffix: 'ms' },
  ];

  return (
    <div className="flex flex-col items-center gap-3 rounded-2xl border border-white/5 bg-gradient-to-b from-neutral-900/60 to-neutral-900/10 p-6">
      <svg viewBox="0 0 200 112" className="w-full max-w-[260px]">
        <defs>
          <linearGradient id="perfGaugeArc" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#ef4444" />
            <stop offset="50%" stopColor="#22c55e" />
            <stop offset="100%" stopColor="#f5b301" />
          </linearGradient>
        </defs>
        {/* track */}
        <path d="M 20 100 A 80 80 0 0 1 180 100" fill="none" stroke="#ffffff14" strokeWidth="12" strokeLinecap="round" />
        {/* colored scale */}
        <path
          d="M 20 100 A 80 80 0 0 1 180 100"
          fill="none"
          stroke="url(#perfGaugeArc)"
          strokeWidth="12"
          strokeLinecap="round"
          strokeOpacity="0.95"
        />
        {/* needle */}
        <motion.line
          x1={100}
          y1={100}
          initial={{ x2: tipX, y2: tipY }}
          animate={{ x2: tipX, y2: tipY }}
          transition={{ type: 'spring', stiffness: 120, damping: 15 }}
          stroke="#ffffff"
          strokeWidth={3}
          strokeLinecap="round"
        />
        <circle cx={100} cy={100} r={6} fill="#ffffff" />
      </svg>
      <div className="-mt-3 flex flex-col items-center">
        <span className="text-2xl font-bold text-white">{pct}%</span>
        <span className="text-[11px] font-medium tracking-wide text-neutral-400">{t('性能增益', 'Performance Boost')}</span>
      </div>
      <div className="grid w-full max-w-[320px] grid-cols-3 gap-2">
        {chips.map((c) => (
          <div key={c.label} className="flex flex-col items-center rounded-lg border border-white/5 bg-black/20 py-2">
            <span className="font-mono text-base font-semibold text-white">
              {c.raw > 0 ? (
                <>
                  <AnimatedNumber value={c.raw} />
                  {c.suffix}
                </>
              ) : (
                '—'
              )}
            </span>
            <span className="text-[10px] uppercase tracking-wide text-neutral-500">{c.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
