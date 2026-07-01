import React, { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { Upload, Trash2 } from 'lucide-react';
import { useT } from '../i18n';

// Every menu-background option (order shown in the picker). panorama_*/classic/shader
// are painted by the Minecraft side; the rest are webview variants (see WEBVIEW_VARIANTS).
export const MENU_BG_OPTIONS: string[] = [
  'panorama_1',
  'classic',
  'aurora',
  'constellation',
  'synthwave',
  'custom',
];

// Background styles rendered *inside* the (transparent) main-menu webview.
// Anything not in this set (panorama_*, classic, shader) is left to the
// Minecraft-side renderer, so MainMenuView stays transparent for those.
export type MenuBgVariant = 'aurora' | 'constellation' | 'synthwave' | 'custom';

const WEBVIEW_VARIANTS: MenuBgVariant[] = ['aurora', 'constellation', 'synthwave', 'custom'];

export const resolveMenuBgVariant = (background: string | undefined | null): MenuBgVariant | null => {
  if (background && (WEBVIEW_VARIANTS as string[]).includes(background)) {
    return background as MenuBgVariant;
  }
  return null;
};

// --- Custom media (image / video), stored client-side so no backend plumbing is needed.
// The ClickGUI and the main menu are the same origin, so localStorage is shared between them.
export const CUSTOM_BG_DATA_KEY = 'fpsmaster.menuBg.customData';
export const CUSTOM_BG_TYPE_KEY = 'fpsmaster.menuBg.customType';
export const CUSTOM_BG_EVENT = 'fpsmaster-menu-bg-changed';

export interface CustomMenuBg {
  data: string;
  type: 'image' | 'video';
}

export const readCustomMenuBg = (): CustomMenuBg | null => {
  try {
    const data = localStorage.getItem(CUSTOM_BG_DATA_KEY);
    const type = localStorage.getItem(CUSTOM_BG_TYPE_KEY);
    if (data && (type === 'image' || type === 'video')) {
      return { data, type };
    }
  } catch {
    /* localStorage unavailable */
  }
  return null;
};

export const writeCustomMenuBg = (bg: CustomMenuBg | null): void => {
  try {
    if (bg) {
      localStorage.setItem(CUSTOM_BG_DATA_KEY, bg.data);
      localStorage.setItem(CUSTOM_BG_TYPE_KEY, bg.type);
    } else {
      localStorage.removeItem(CUSTOM_BG_DATA_KEY);
      localStorage.removeItem(CUSTOM_BG_TYPE_KEY);
    }
    window.dispatchEvent(new Event(CUSTOM_BG_EVENT));
  } catch {
    /* localStorage unavailable */
  }
};

// ---------------------------------------------------------------------------
// Aurora — a starry night sky with shimmering northern-lights curtains.
// ---------------------------------------------------------------------------
const STAR_FIELD =
  'radial-gradient(1px 1px at 15% 25%, rgba(255,255,255,0.8), transparent),' +
  'radial-gradient(1px 1px at 70% 18%, rgba(255,255,255,0.55), transparent),' +
  'radial-gradient(1.4px 1.4px at 42% 58%, rgba(255,255,255,0.5), transparent),' +
  'radial-gradient(1px 1px at 85% 44%, rgba(255,255,255,0.55), transparent),' +
  'radial-gradient(1px 1px at 55% 12%, rgba(255,255,255,0.7), transparent),' +
  'radial-gradient(1.2px 1.2px at 30% 40%, rgba(255,255,255,0.45), transparent),' +
  'radial-gradient(1px 1px at 92% 70%, rgba(255,255,255,0.5), transparent)';

const AuroraBackground: React.FC = () => (
  <div className="absolute inset-0 overflow-hidden bg-gradient-to-b from-[#0a1024] via-[#070a16] to-[#03040a]">
    <div className="absolute inset-0 opacity-70" style={{ backgroundImage: STAR_FIELD }} />
    <motion.div
      className="absolute -top-32 left-[-20%] h-[75%] w-[70%] rotate-[18deg] blur-[70px]"
      style={{ background: 'linear-gradient(100deg, transparent, rgba(52,211,153,0.38) 40%, rgba(34,211,238,0.32) 62%, transparent)' }}
      animate={{ x: ['-6%', '10%', '-6%'], opacity: [0.5, 0.95, 0.5] }}
      transition={{ duration: 14, repeat: Infinity, ease: 'easeInOut' }}
    />
    <motion.div
      className="absolute -top-24 right-[-15%] h-[70%] w-[60%] -rotate-[14deg] blur-[80px]"
      style={{ background: 'linear-gradient(80deg, transparent, rgba(129,140,248,0.34) 45%, rgba(217,70,239,0.28) 66%, transparent)' }}
      animate={{ x: ['6%', '-8%', '6%'], opacity: [0.45, 0.85, 0.45] }}
      transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }}
    />
    <motion.div
      className="absolute top-[4%] left-[8%] h-[62%] w-[58%] rotate-[8deg] blur-[95px]"
      style={{ background: 'linear-gradient(120deg, transparent, rgba(45,212,191,0.28) 50%, transparent)' }}
      animate={{ x: ['0%', '12%', '0%'], opacity: [0.4, 0.78, 0.4] }}
      transition={{ duration: 22, repeat: Infinity, ease: 'easeInOut' }}
    />
    <div className="absolute inset-0 bg-gradient-to-t from-black/75 via-transparent to-transparent" />
    <div className="absolute inset-0" style={{ boxShadow: 'inset 0 0 220px 70px rgba(0,0,0,0.55)' }} />
  </div>
);

// ---------------------------------------------------------------------------
// Constellation — drifting particles that link with thin lines when near.
// ---------------------------------------------------------------------------
const ConstellationBackground: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let raf = 0;
    let w = 0;
    let h = 0;
    const DENSITY = 0.00009; // particles per px²
    let particles: { x: number; y: number; vx: number; vy: number }[] = [];

    // Deterministic-ish spread without Math.random dependence at module load.
    let seed = 1337;
    const rand = () => {
      seed = (seed * 1664525 + 1013904223) & 0x7fffffff;
      return seed / 0x7fffffff;
    };

    const build = () => {
      const parent = canvas.parentElement;
      w = parent ? parent.clientWidth : canvas.clientWidth;
      h = parent ? parent.clientHeight : canvas.clientHeight;
      canvas.width = w;
      canvas.height = h;
      const count = Math.max(40, Math.min(140, Math.floor(w * h * DENSITY)));
      particles = Array.from({ length: count }, () => ({
        x: rand() * w,
        y: rand() * h,
        vx: (rand() - 0.5) * 0.25,
        vy: (rand() - 0.5) * 0.25,
      }));
    };

    const LINK = 130;
    const draw = () => {
      ctx.clearRect(0, 0, w, h);
      for (const p of particles) {
        p.x += p.vx;
        p.y += p.vy;
        if (p.x < 0 || p.x > w) p.vx *= -1;
        if (p.y < 0 || p.y > h) p.vy *= -1;
      }
      for (let i = 0; i < particles.length; i++) {
        const a = particles[i];
        for (let j = i + 1; j < particles.length; j++) {
          const b = particles[j];
          const dx = a.x - b.x;
          const dy = a.y - b.y;
          const dist = Math.hypot(dx, dy);
          if (dist < LINK) {
            ctx.strokeStyle = `rgba(129,140,248,${0.16 * (1 - dist / LINK)})`;
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
            ctx.stroke();
          }
        }
        ctx.fillStyle = 'rgba(165,180,252,0.75)';
        ctx.beginPath();
        ctx.arc(a.x, a.y, 1.6, 0, Math.PI * 2);
        ctx.fill();
      }
      raf = requestAnimationFrame(draw);
    };

    build();
    draw();
    const onResize = () => build();
    window.addEventListener('resize', onResize);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener('resize', onResize);
    };
  }, []);

  return (
    <div className="absolute inset-0 overflow-hidden bg-[#05060a]">
      <div className="absolute inset-0 bg-gradient-to-b from-indigo-950/40 via-transparent to-black/50" />
      <canvas ref={canvasRef} className="absolute inset-0 h-full w-full" />
    </div>
  );
};

// ---------------------------------------------------------------------------
// Synthwave — perspective grid receding to a glowing horizon + soft sun.
// ---------------------------------------------------------------------------
const SynthwaveBackground: React.FC = () => (
  <div className="absolute inset-0 overflow-hidden bg-gradient-to-b from-[#2a0a4d] via-[#180733] to-[#080213]">
    {/* Sun with horizontal scanline bands */}
    <div className="absolute left-1/2 top-[22%] h-60 w-60 -translate-x-1/2">
      <div
        className="h-full w-full rounded-full"
        style={{ background: 'linear-gradient(#fde68a 0%, #fb7185 55%, #d946ef 100%)' }}
      />
      <div
        className="absolute inset-0 rounded-full"
        style={{
          backgroundImage: 'repeating-linear-gradient(to bottom, transparent 0 10px, rgba(8,2,19,0.9) 10px 15px)',
          maskImage: 'linear-gradient(to bottom, transparent 45%, black 55%)',
          WebkitMaskImage: 'linear-gradient(to bottom, transparent 45%, black 55%)',
        }}
      />
    </div>
    <div className="absolute left-1/2 top-[22%] h-72 w-72 -translate-x-1/2 rounded-full bg-fuchsia-500/40 blur-[70px]" />

    {/* Horizon glow line */}
    <div
      className="absolute left-0 right-0 top-1/2 h-[2px] bg-[#f0abfc]"
      style={{ boxShadow: '0 0 22px 5px rgba(240,171,252,0.8)' }}
    />

    {/* Perspective grid (inline styles for reliable transform/perspective in CEF) */}
    <div
      className="absolute inset-x-0 bottom-0 top-1/2 overflow-hidden"
      style={{ perspective: '260px', perspectiveOrigin: '50% 0%' }}
    >
      <motion.div
        className="absolute left-[-50%] top-0 h-[240%] w-[200%]"
        style={{
          transformOrigin: '50% 0%',
          transform: 'rotateX(76deg)',
          backgroundImage:
            'linear-gradient(rgba(236,72,153,0.9) 0 2px, transparent 2px), linear-gradient(90deg, rgba(236,72,153,0.9) 0 2px, transparent 2px)',
          backgroundSize: '64px 64px',
        }}
        animate={{ backgroundPositionY: ['0px', '64px'] }}
        transition={{ duration: 1.2, repeat: Infinity, ease: 'linear' }}
      />
    </div>

    {/* Fade the grid's far edge into the horizon */}
    <div className="absolute inset-x-0 top-1/2 h-20 bg-gradient-to-b from-[#180733] to-transparent" />
    <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent" />
  </div>
);

// ---------------------------------------------------------------------------
// Custom — a user-supplied image or looping video (stored in localStorage).
// ---------------------------------------------------------------------------
const CustomBackground: React.FC = () => {
  const [bg, setBg] = useState<CustomMenuBg | null>(readCustomMenuBg);

  useEffect(() => {
    const refresh = () => setBg(readCustomMenuBg());
    window.addEventListener(CUSTOM_BG_EVENT, refresh);
    window.addEventListener('storage', refresh);
    return () => {
      window.removeEventListener(CUSTOM_BG_EVENT, refresh);
      window.removeEventListener('storage', refresh);
    };
  }, []);

  if (!bg) {
    return (
      <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-b from-[#151a20] to-[#05060a] text-xs uppercase tracking-widest text-neutral-600">
        <span className="opacity-60">no custom background set</span>
      </div>
    );
  }

  return (
    <div className="absolute inset-0 overflow-hidden bg-black">
      {bg.type === 'video' ? (
        <video className="h-full w-full object-cover" src={bg.data} autoPlay loop muted playsInline />
      ) : (
        <img className="h-full w-full object-cover" src={bg.data} alt="" />
      )}
      <div className="absolute inset-0 bg-black/25" />
    </div>
  );
};

const MAX_CUSTOM_BG_BYTES = 4 * 1024 * 1024;

// Upload / clear the custom menu background (image or looping video). Stored in localStorage,
// shared with the main-menu webview (same origin), so no backend/packet plumbing is needed.
export const CustomBackgroundUploader: React.FC = () => {
  const t = useT();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [current, setCurrent] = useState<CustomMenuBg | null>(readCustomMenuBg);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const refresh = () => setCurrent(readCustomMenuBg());
    window.addEventListener(CUSTOM_BG_EVENT, refresh);
    return () => window.removeEventListener(CUSTOM_BG_EVENT, refresh);
  }, []);

  const onPick = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (file.size > MAX_CUSTOM_BG_BYTES) {
      setError(t('bg.custom.tooLarge'));
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const data = String(reader.result || '');
      const type: 'image' | 'video' = file.type.startsWith('video') ? 'video' : 'image';
      writeCustomMenuBg({ data, type });
      setError(null);
    };
    reader.readAsDataURL(file);
  };

  return (
    <div className="mt-1 flex flex-col gap-2">
      <input ref={inputRef} type="file" accept="image/*,video/*" className="hidden" onChange={onPick} />
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-white/[0.04] px-3 py-1.5 text-xs font-medium text-neutral-200 transition-colors hover:bg-white/[0.08]"
        >
          <Upload size={13} /> {current ? t('bg.custom.replace') : t('bg.custom.upload')}
        </button>
        {current && (
          <button
            type="button"
            onClick={() => writeCustomMenuBg(null)}
            className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-white/[0.04] px-3 py-1.5 text-xs font-medium text-neutral-400 transition-colors hover:bg-red-500/10 hover:text-red-200"
          >
            <Trash2 size={13} /> {t('bg.custom.clear')}
          </button>
        )}
      </div>
      <span className="text-[11px] text-neutral-500">{error || t('bg.custom.hint')}</span>
    </div>
  );
};

export const MainMenuBackground: React.FC<{ variant: MenuBgVariant }> = ({ variant }) => {
  switch (variant) {
    case 'aurora':
      return <AuroraBackground />;
    case 'constellation':
      return <ConstellationBackground />;
    case 'synthwave':
      return <SynthwaveBackground />;
    case 'custom':
      return <CustomBackground />;
    default:
      return null;
  }
};
