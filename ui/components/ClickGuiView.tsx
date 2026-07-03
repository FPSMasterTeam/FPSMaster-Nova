import React, { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { Sidebar } from './Sidebar';
import { SettingsPanel } from './SettingsPanel';
import { TabId } from '../types';
import { NetworkManager } from '../network/WebSocketClient';
import { ModuleValueUpdatePacket, RemoteModuleValueType } from '../network/packets/ModulePackets';
import type { ClickGuiConfig, ViewState } from '../App';

// Fallback panel size if a config value is ever 0/invalid (mirrors DEFAULT_CLICK_GUI_CONFIG in App).
const FALLBACK_PANEL_WIDTH = 950;
const FALLBACK_PANEL_HEIGHT = 620;

interface PerformanceMetrics {
  fps: number;
  frameAvgMs: number | null;
  frameP95Ms: number | null;
  droppedFrames: number;
  inp: number | null;
  longTasks: number;
  longestTaskMs: number;
  heapUsedMb: number | null;
  heapTotalMb: number | null;
  viewport: string;
  uptimeSec: number;
}

interface PerformanceMemoryInfo {
  usedJSHeapSize: number;
  totalJSHeapSize: number;
}

const INITIAL_PERFORMANCE_METRICS: PerformanceMetrics = {
  fps: 0,
  frameAvgMs: null,
  frameP95Ms: null,
  droppedFrames: 0,
  inp: null,
  longTasks: 0,
  longestTaskMs: 0,
  heapUsedMb: null,
  heapTotalMb: null,
  viewport: '-',
  uptimeSec: 0,
};

const usePerformanceMetrics = (enabled: boolean): PerformanceMetrics => {
  const [metrics, setMetrics] = useState<PerformanceMetrics>(INITIAL_PERFORMANCE_METRICS);
  const frameCountRef = useRef(0);
  const lastSampleRef = useRef(performance.now());
  const lastFrameAtRef = useRef<number | null>(null);
  const frameDurationsRef = useRef<number[]>([]);
  const animationFrameRef = useRef<number | null>(null);
  const inpRef = useRef<number | null>(null);
  const longTasksRef = useRef(0);
  const longestTaskRef = useRef(0);
  const droppedFramesRef = useRef(0);
  const startedAtRef = useRef(performance.now());

  useEffect(() => {
    if (!enabled) {
      if (animationFrameRef.current !== null) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
      setMetrics(INITIAL_PERFORMANCE_METRICS);
      return undefined;
    }

    frameCountRef.current = 0;
    lastSampleRef.current = performance.now();
    lastFrameAtRef.current = null;
    frameDurationsRef.current = [];
    inpRef.current = null;
    longTasksRef.current = 0;
    longestTaskRef.current = 0;
    droppedFramesRef.current = 0;
    startedAtRef.current = performance.now();

    const updateFps = (now: number) => {
      if (lastFrameAtRef.current !== null) {
        const frameDuration = now - lastFrameAtRef.current;
        frameDurationsRef.current.push(frameDuration);
        if (frameDuration > 34) {
          droppedFramesRef.current += Math.max(1, Math.round(frameDuration / 16.67) - 1);
        }
      }
      lastFrameAtRef.current = now;
      frameCountRef.current += 1;
      const elapsed = now - lastSampleRef.current;
      if (elapsed >= 1000) {
        const durations = frameDurationsRef.current;
        const sortedDurations = [...durations].sort((a, b) => a - b);
        const frameAvgMs =
          durations.length === 0
            ? null
            : Math.round((durations.reduce((sum, duration) => sum + duration, 0) / durations.length) * 10) / 10;
        const frameP95Ms =
          sortedDurations.length === 0
            ? null
            : Math.round(sortedDurations[Math.floor((sortedDurations.length - 1) * 0.95)] * 10) / 10;
        const memory = (performance as Performance & { memory?: PerformanceMemoryInfo }).memory;
        const heapUsedMb = memory ? Math.round(memory.usedJSHeapSize / 1024 / 1024) : null;
        const heapTotalMb = memory ? Math.round(memory.totalJSHeapSize / 1024 / 1024) : null;

        setMetrics({
          fps: Math.round((frameCountRef.current * 1000) / elapsed),
          frameAvgMs,
          frameP95Ms,
          droppedFrames: droppedFramesRef.current,
          inp: inpRef.current,
          longTasks: longTasksRef.current,
          longestTaskMs: longestTaskRef.current,
          heapUsedMb,
          heapTotalMb,
          viewport: `${window.innerWidth}x${window.innerHeight}`,
          uptimeSec: Math.round((now - startedAtRef.current) / 1000),
        });
        frameCountRef.current = 0;
        frameDurationsRef.current = [];
        lastSampleRef.current = now;
      }
      animationFrameRef.current = requestAnimationFrame(updateFps);
    };

    animationFrameRef.current = requestAnimationFrame(updateFps);

    const observers: PerformanceObserver[] = [];
    if ('PerformanceObserver' in window) {
      try {
        const eventObserver = new PerformanceObserver((list) => {
          list.getEntries().forEach((entry) => {
            const duration = Math.round(entry.duration);
            inpRef.current = Math.max(inpRef.current ?? 0, duration);
          });
        });
        eventObserver.observe({ type: 'event', buffered: true, durationThreshold: 16 });
        observers.push(eventObserver);
      } catch (_error) {
        // Event Timing is not available in every embedded Chromium build.
      }

      try {
        const longTaskObserver = new PerformanceObserver((list) => {
          const entries = list.getEntries();
          longTasksRef.current += entries.length;
          entries.forEach((entry) => {
            longestTaskRef.current = Math.max(longestTaskRef.current, Math.round(entry.duration));
          });
        });
        longTaskObserver.observe({ type: 'longtask', buffered: true });
        observers.push(longTaskObserver);
      } catch (_error) {
        // Long Task timing is optional in some browser contexts.
      }
    }

    return () => {
      if (animationFrameRef.current !== null) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
      observers.forEach((observer) => observer.disconnect());
    };
  }, [enabled]);

  return metrics;
};

interface ClickGuiViewProps {
  viewState: ViewState;
  wsStatus: string;
  clickGuiConfig: ClickGuiConfig;
  version: string;
}

export const ClickGuiView: React.FC<ClickGuiViewProps> = ({ viewState, wsStatus, clickGuiConfig, version }) => {
  const [activeTab, setActiveTab] = useState<TabId>(TabId.FEATURES);
  const [immersiveMode, setImmersiveMode] = useState(false);
  const performanceMetrics = usePerformanceMetrics(clickGuiConfig.developerMetrics);

  // Draggable window size (replaces the width/height sliders). Persisted back to clickgui.width/height.
  const [panelSize, setPanelSize] = useState({ width: clickGuiConfig.width, height: clickGuiConfig.height });
  const panelSizeRef = useRef(panelSize);
  const resizingRef = useRef(false);

  useEffect(() => {
    if (!resizingRef.current) {
      const next = { width: clickGuiConfig.width, height: clickGuiConfig.height };
      panelSizeRef.current = next;
      setPanelSize(next);
    }
  }, [clickGuiConfig.width, clickGuiConfig.height]);

  const animationEnabled = clickGuiConfig.enabled && clickGuiConfig.animationsEnabled;
  const showBackground = clickGuiConfig.enabled && clickGuiConfig.backgroundEnabled;
  const showBranding = clickGuiConfig.enabled && clickGuiConfig.brandingVisible;
  const visibleDuration = animationEnabled ? 0.4 : 0.01;
  const closingDuration = animationEnabled ? 0.25 : 0.01;
  const childDelay = animationEnabled ? 0.1 : 0;
  const childStagger = animationEnabled ? 0.05 : 0;

  const containerVariants = {
    hidden: {
      opacity: 0,
      scale: animationEnabled ? 0.92 : 1,
      filter: animationEnabled ? 'blur(10px)' : 'blur(0px)',
    },
    refresh: {
      opacity: 0,
      scale: animationEnabled ? 0.96 : 1,
      filter: animationEnabled ? 'blur(6px)' : 'blur(0px)',
    },
    visible: {
      opacity: 1,
      scale: 1,
      filter: 'blur(0px)',
      transition: {
        duration: visibleDuration,
        ease: [0.22, 1, 0.36, 1],
        delayChildren: childDelay,
        staggerChildren: childStagger,
      },
    },
    closing: {
      opacity: 0,
      scale: animationEnabled ? 0.96 : 1,
      filter: animationEnabled ? 'blur(6px)' : 'blur(0px)',
      transition: {
        duration: closingDuration,
        ease: [0.22, 1, 0.36, 1],
      },
    },
  };

  const overlayVariants = {
    hidden: { opacity: 0 },
    refresh: { opacity: 0 },
    visible: {
      opacity: showBackground ? 0.5 : 0,
      transition: { duration: visibleDuration, ease: [0.22, 1, 0.36, 1] },
    },
    closing: {
      opacity: 0,
      transition: { duration: closingDuration, ease: [0.22, 1, 0.36, 1] },
    },
  };

  const logoVariants = {
    hidden: { opacity: 0 },
    refresh: { opacity: 0 },
    visible: { opacity: 1, transition: { duration: visibleDuration } },
    closing: { opacity: 0, transition: { duration: closingDuration } },
  };

  const sidebarVariants = {
    hidden: {
      opacity: 0,
      scale: animationEnabled ? 0.96 : 1,
      filter: animationEnabled ? 'blur(6px)' : 'blur(0px)',
    },
    refresh: {
      opacity: 1,
      scale: 1,
      filter: 'blur(0px)',
    },
    visible: {
      opacity: 1,
      scale: 1,
      filter: 'blur(0px)',
      transition: { duration: visibleDuration, ease: [0.22, 1, 0.36, 1] },
    },
    closing: {
      opacity: 0,
      scale: animationEnabled ? 0.96 : 1,
      filter: animationEnabled ? 'blur(6px)' : 'blur(0px)',
      transition: { duration: closingDuration, ease: [0.22, 1, 0.36, 1] },
    },
  };

  const childVariants = {
    hidden: { opacity: 0, y: animationEnabled ? 15 : 0 },
    refresh: { opacity: 1, y: 0 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: animationEnabled ? 0.3 : 0.01,
        ease: [0.22, 1, 0.36, 1],
      },
    },
    closing: {
      opacity: 0,
      y: 0,
      transition: {
        duration: closingDuration,
        ease: [0.22, 1, 0.36, 1],
      },
    },
  };

  const PANEL_MIN_W = 640;
  const PANEL_MAX_W = 1600;
  const PANEL_MIN_H = 420;
  const PANEL_MAX_H = 1000;
  const clampSize = (w: number, h: number) => ({
    width: Math.round(Math.min(PANEL_MAX_W, Math.max(PANEL_MIN_W, w))),
    height: Math.round(Math.min(PANEL_MAX_H, Math.max(PANEL_MIN_H, h))),
  });

  // Drag a window edge/corner to resize; a centered modal grows symmetrically (2x the drag delta).
  // Persisted to clickgui.width/height on release.
  const beginResize = (dirX: number, dirY: number) => (e: React.PointerEvent) => {
    e.preventDefault();
    e.stopPropagation();
    resizingRef.current = true;
    const startX = e.clientX;
    const startY = e.clientY;
    const startW = panelSizeRef.current.width;
    const startH = panelSizeRef.current.height;
    const onMove = (ev: PointerEvent) => {
      const next = clampSize(startW + dirX * (ev.clientX - startX) * 2, startH + dirY * (ev.clientY - startY) * 2);
      panelSizeRef.current = next;
      setPanelSize(next);
    };
    const onUp = () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      resizingRef.current = false;
      const { width, height } = panelSizeRef.current;
      if (wsStatus === 'open') {
        NetworkManager.send(new ModuleValueUpdatePacket('clickgui', 'width', RemoteModuleValueType.NUMBER, width));
        NetworkManager.send(new ModuleValueUpdatePacket('clickgui', 'height', RemoteModuleValueType.NUMBER, height));
      }
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  };

  const resizeHandles = [
    { cls: 'top-0 left-2 right-2 h-1.5 cursor-ns-resize', dx: 0, dy: -1 },
    { cls: 'bottom-0 left-2 right-2 h-1.5 cursor-ns-resize', dx: 0, dy: 1 },
    { cls: 'top-2 bottom-2 left-0 w-1.5 cursor-ew-resize', dx: -1, dy: 0 },
    { cls: 'top-2 bottom-2 right-0 w-1.5 cursor-ew-resize', dx: 1, dy: 0 },
    { cls: 'top-0 left-0 h-3 w-3 cursor-nwse-resize', dx: -1, dy: -1 },
    { cls: 'top-0 right-0 h-3 w-3 cursor-nesw-resize', dx: 1, dy: -1 },
    { cls: 'bottom-0 left-0 h-3 w-3 cursor-nesw-resize', dx: -1, dy: 1 },
    { cls: 'bottom-0 right-0 h-3 w-3 cursor-nwse-resize', dx: 1, dy: 1 },
  ];

  return (
    <div className="fixed inset-0 flex items-center justify-center font-sans antialiased selection:bg-indigo-500/30 text-slate-200 p-4">
      <motion.div
        initial="hidden"
        animate={viewState}
        variants={overlayVariants}
        className="fixed inset-0 bg-black"
        style={{ zIndex: 0 }}
      />

      {showBranding ? (
        <div className="fixed bottom-8 left-8 z-0 pointer-events-none select-none">
          <motion.div
            initial="hidden"
            animate={viewState}
            variants={logoVariants}
            style={{ willChange: 'opacity, transform' }}
          >
            <div className="text-2xl font-bold text-white tracking-tight drop-shadow-lg">
              FPSMaster <span className="text-indigo-400">Nova</span>
            </div>
            {version ? (
              <div className="mt-2 flex">
                <span className="px-2 py-0.5 rounded-md bg-white/5 text-xs font-mono text-neutral-400 border border-white/5 backdrop-blur-sm">
                  {version}
                </span>
              </div>
            ) : null}
          </motion.div>
        </div>
      ) : null}

      <motion.div
        initial="hidden"
        animate={viewState}
        variants={containerVariants}
        className="fpsmaster-panel bg-[#0a0a0a]/90 border border-white/10 rounded-xl shadow-[0_25px_50px_-12px_rgba(0,0,0,0.5)] flex overflow-hidden ring-1 ring-white/5 relative z-10"
        style={{
          width: `min(calc(100vw - 2rem), ${panelSize.width || FALLBACK_PANEL_WIDTH}px)`,
          height: `min(calc(100vh - 2rem), ${panelSize.height || FALLBACK_PANEL_HEIGHT}px)`,
          transformOrigin: 'center center',
          willChange: 'opacity, transform, filter',
        }}
      >
        {resizeHandles.map((h, i) => (
          <div key={i} onPointerDown={beginResize(h.dx, h.dy)} className={`absolute z-30 ${h.cls}`} />
        ))}
        {showBackground ? (
          <div className="absolute top-0 right-0 w-[400px] h-[400px] bg-indigo-600/10 rounded-full blur-[100px] translate-x-1/2 -translate-y-1/2 pointer-events-none" />
        ) : null}

        <motion.div
          initial="hidden"
          animate={viewState}
          variants={sidebarVariants}
          className="shrink-0 border-r border-white/5 bg-black/20 h-full"
        >
          <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} />
        </motion.div>

        <motion.div variants={childVariants} className="flex-1 relative min-w-0 flex flex-col h-full">
          <SettingsPanel
            activeTab={activeTab}
            immersiveMode={immersiveMode}
            setImmersiveMode={setImmersiveMode}
            wsStatus={wsStatus}
          />
        </motion.div>
      </motion.div>

      {clickGuiConfig.developerMetrics ? (
        <div className="fixed top-4 right-4 z-50 grid min-w-44 pointer-events-none select-none gap-1.5 rounded-lg border border-white/10 bg-black/75 px-3 py-2 font-mono text-[10px] leading-4 text-neutral-200 shadow-xl backdrop-blur-md">
          <div className="mb-0.5 flex items-center justify-between border-b border-white/10 pb-1 text-[9px] uppercase tracking-[0.14em] text-neutral-500">
            <span>DEV</span>
            <span>{performanceMetrics.uptimeSec}s</span>
          </div>
          <div className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-0.5">
            <span className="text-neutral-500">FPS</span>
            <span className="text-right">{performanceMetrics.fps}</span>
            <span className="text-neutral-500">FRAME</span>
            <span className="text-right">
              {performanceMetrics.frameAvgMs === null ? '-' : `${performanceMetrics.frameAvgMs}ms`}
            </span>
            <span className="text-neutral-500">P95</span>
            <span className="text-right">
              {performanceMetrics.frameP95Ms === null ? '-' : `${performanceMetrics.frameP95Ms}ms`}
            </span>
            <span className="text-neutral-500">DROP</span>
            <span className="text-right">{performanceMetrics.droppedFrames}</span>
          </div>
          <div className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-0.5 border-t border-white/10 pt-1">
            <span className="text-neutral-500">INP</span>
            <span className="text-right">{performanceMetrics.inp === null ? '-' : `${performanceMetrics.inp}ms`}</span>
            <span className="text-neutral-500">LONG</span>
            <span className="text-right">
              {performanceMetrics.longTasks}
              {performanceMetrics.longestTaskMs > 0 ? ` / ${performanceMetrics.longestTaskMs}ms` : ''}
            </span>
            <span className="text-neutral-500">HEAP</span>
            <span className="text-right">
              {performanceMetrics.heapUsedMb === null || performanceMetrics.heapTotalMb === null
                ? '-'
                : `${performanceMetrics.heapUsedMb}/${performanceMetrics.heapTotalMb}MB`}
            </span>
          </div>
          <div className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-0.5 border-t border-white/10 pt-1">
            <span className="text-neutral-500">VIEW</span>
            <span className="text-right">{performanceMetrics.viewport}</span>
            <span className="text-neutral-500">WS</span>
            <span className="text-right">{wsStatus}</span>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default ClickGuiView;
