import React, { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';

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
// Aurora — a few large blurred colour blobs drifting over a near-black base.
// ---------------------------------------------------------------------------
const AuroraBackground: React.FC = () => (
  <div className="absolute inset-0 overflow-hidden bg-[#05060a]">
    <motion.div
      className="absolute -left-32 -top-40 h-[620px] w-[620px] rounded-full bg-indigo-600/30 blur-[130px]"
      animate={{ x: [0, 60, 0], y: [0, 40, 0], scale: [1, 1.12, 1] }}
      transition={{ duration: 20, repeat: Infinity, ease: 'easeInOut' }}
    />
    <motion.div
      className="absolute right-[-10%] top-[-15%] h-[560px] w-[560px] rounded-full bg-fuchsia-600/20 blur-[130px]"
      animate={{ x: [0, -50, 0], y: [0, 50, 0], scale: [1, 1.15, 1] }}
      transition={{ duration: 26, repeat: Infinity, ease: 'easeInOut' }}
    />
    <motion.div
      className="absolute bottom-[-20%] left-[20%] h-[600px] w-[600px] rounded-full bg-cyan-500/20 blur-[140px]"
      animate={{ x: [0, 40, 0], y: [0, -40, 0], scale: [1, 1.1, 1] }}
      transition={{ duration: 23, repeat: Infinity, ease: 'easeInOut' }}
    />
    <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-black/30" />
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
  <div className="absolute inset-0 overflow-hidden bg-gradient-to-b from-[#1a0b2e] via-[#0d0616] to-black">
    {/* Sun */}
    <div className="absolute left-1/2 top-[26%] h-64 w-64 -translate-x-1/2 rounded-full bg-gradient-to-b from-fuchsia-500 to-orange-400 opacity-80 blur-[2px]" />
    <div className="absolute left-1/2 top-[26%] h-72 w-72 -translate-x-1/2 rounded-full bg-fuchsia-500/40 blur-[60px]" />
    {/* Horizon glow */}
    <div className="absolute left-0 right-0 top-[52%] h-px bg-fuchsia-400/70 shadow-[0_0_24px_6px_rgba(232,121,249,0.5)]" />
    {/* Perspective grid */}
    <div className="absolute inset-x-0 bottom-0 top-[52%] overflow-hidden [perspective:340px]">
      <motion.div
        className="absolute inset-x-[-50%] bottom-[-40%] top-0 [transform-origin:top_center] [transform:rotateX(72deg)]"
        style={{
          backgroundImage:
            'linear-gradient(rgba(232,121,249,0.5) 1px, transparent 1px), linear-gradient(90deg, rgba(232,121,249,0.5) 1px, transparent 1px)',
          backgroundSize: '46px 46px',
        }}
        animate={{ backgroundPositionY: ['0px', '46px'] }}
        transition={{ duration: 1.6, repeat: Infinity, ease: 'linear' }}
      />
    </div>
    <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent" />
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
