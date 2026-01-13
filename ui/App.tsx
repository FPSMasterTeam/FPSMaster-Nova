import React, { useEffect, useRef, useState } from 'react';
import { Sidebar } from './components/Sidebar';
import { SettingsPanel } from './components/SettingsPanel';
import { TabId } from './types';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronDown, SkipBack, Play, Pause, SkipForward, Repeat, Shuffle, Heart } from 'lucide-react';

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabId>(TabId.OPTIMIZE);
  const [immersiveMode, setImmersiveMode] = useState(false);
  const [wsStatus, setWsStatus] = useState('idle');
  const wsRef = useRef<WebSocket | null>(null);
  const [animKey, setAnimKey] = useState(0);
  const [viewState, setViewState] = useState<'visible' | 'closing'>('visible');
  const encodeAck = (success: boolean, message: string, timestamp: number) => {
    const msgBytes = new TextEncoder().encode(message);
    const len = 1 + 4 + msgBytes.length + 8;
    const buf = new ArrayBuffer(len);
    const view = new DataView(buf);
    let o = 0;
    view.setUint8(o, success ? 1 : 0); o += 1;
    view.setInt32(o, msgBytes.length, false); o += 4;
    new Uint8Array(buf, o, msgBytes.length).set(msgBytes); o += msgBytes.length;
    const hi = Math.floor(timestamp / 2 ** 32);
    const lo = timestamp >>> 0;
    view.setUint32(o, hi, false); o += 4;
    view.setUint32(o, lo, false); o += 4;
    const b64 = btoa(String.fromCharCode(...Array.from(new Uint8Array(buf))));
    return JSON.stringify({ packetId: 10, data: b64 });
  };
  const decodeEventType = (b64: string): string | null => {
    try {
      const bin = atob(b64);
      const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      const view = new DataView(bytes.buffer);
      let o = 0;
      const len1 = view.getInt32(o, false); o += 4;
      if (len1 < 0) return null;
      const s1 = new TextDecoder().decode(bytes.subarray(o, o + len1)); o += len1;
      // skip long 8 bytes
      o += 8;
      // skip extraData string
      const len2 = view.getInt32(o, false); o += 4;
      if (len2 >= 0) o += len2;
      return s1;
    } catch {
      return null;
    }
  };

  useEffect(() => {
    const url = 'ws://localhost:4399/websocket';
    console.info(`[WS] connect ${url} at ${Date.now()}`);
    const ws = new WebSocket(url);
    wsRef.current = ws;
    (window as any).fps_ws = ws;
    ws.onopen = () => {
      setWsStatus('open');
      console.info(`[WS] open at ${Date.now()}`);
    };
    ws.onclose = e => {
      setWsStatus(`close:${e.code}`);
      console.warn(`[WS] close code=${e.code} reason=${e.reason} at ${Date.now()}`);
    };
    ws.onerror = e => {
      setWsStatus('error');
      console.error('[WS] error', e);
    };
    ws.onmessage = e => {
      console.debug(`[WS] message size=${String(e.data).length} at ${Date.now()}`);
      try {
        const obj = JSON.parse(String(e.data));
        if (typeof obj.packetId === 'number' && obj.packetId === 9) {
          const eventType = typeof obj.data === 'string' ? decodeEventType(obj.data) : null;
          if (eventType === 'close') {
            setViewState('closing');
            setTimeout(() => {
              const json = encodeAck(true, 'GUI close event received', Date.now());
              ws.send(json);
              console.info('[WS] sent GuiCloseAck after closing animation');
            }, 250);
          } else {
            // open/refresh
            setViewState('refresh');
            requestAnimationFrame(() => {
              setViewState('visible');
              requestAnimationFrame(() => {
                const json = encodeAck(true, 'GUI load event received', Date.now());
                ws.send(json);
                console.info('[WS] sent GuiLoadAck (after anim prep)');
              });
            });
          }
        }
      } catch {}
    };
    return () => {};
  }, []);

  // App startup animation variants
  const containerVariants = {
    hidden: {
      opacity: 0,
      scale: 0.92,
      filter: "blur(10px)"
    },
    refresh: {
      opacity: 0,
      scale: 0.96,
      filter: "blur(6px)"
    },
    visible: {
      opacity: 1,
      scale: 1,
      filter: "blur(0px)",
      transition: {
        duration: 0.4,
        ease: [0.22, 1, 0.36, 1],
        delayChildren: 0.1,
        staggerChildren: 0.05
      }
    },
    closing: {
      opacity: 0,
      scale: 0.96,
      filter: "blur(6px)",
      transition: {
        duration: 0.25,
        ease: [0.22, 1, 0.36, 1]
      }
    }
  };

  const overlayVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 0.5,
      transition: { duration: 0.4, ease: [0.22, 1, 0.36, 1] }
    },
    closing: {
      opacity: 0,
      transition: { duration: 0.25, ease: [0.22, 1, 0.36, 1] }
    }
  };

  const logoVariants = {
    hidden: { opacity: 0 },
    visible: { opacity: 1, transition: { duration: 0.5 } },
    closing: { opacity: 0, transition: { duration: 0.25 } }
  };

  const sidebarVariants = {
    hidden: { opacity: 0, scale: 0.96, filter: "blur(6px)" },
    visible: { 
      opacity: 1, 
      scale: 1, 
      filter: "blur(0px)",
      transition: { duration: 0.4, ease: [0.22, 1, 0.36, 1] }
    },
    closing: { 
      opacity: 0, 
      scale: 0.96, 
      filter: "blur(6px)",
      transition: { duration: 0.25, ease: [0.22, 1, 0.36, 1] }
    }
  };

  const childVariants = {
      hidden: { opacity: 0, y: 15 },
      visible: { 
        opacity: 1, 
        y: 0, 
        transition: { 
          duration: 0.3, 
          ease: [0.22, 1, 0.36, 1] 
        } 
      }
  };

  return (
    <div className="fixed inset-0 flex items-center justify-center font-sans antialiased selection:bg-indigo-500/30 text-slate-200 p-4">
      
      <motion.div
        initial="hidden"
        animate={viewState}
        variants={overlayVariants}
        className="fixed inset-0 bg-black backdrop-blur-sm"
        style={{ zIndex: 0 }}
      />
      
      {/* Page Level Branding (Fixed to viewport) */}
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
              <div className="mt-2 flex">
                  <span className="px-2 py-0.5 rounded-md bg-white/5 text-xs font-mono text-neutral-400 border border-white/5 backdrop-blur-sm">
                      4.0.0 beta
                  </span>
                  <span className="ml-2 px-2 py-0.5 rounded-md bg-white/5 text-xs font-mono text-neutral-400 border border-white/5 backdrop-blur-sm">
                      WS:{wsStatus}
                  </span>
              </div>
          </motion.div>
      </div>

      {/* Main Window */}
      <motion.div 
        initial="hidden"
        animate={viewState}
        variants={containerVariants}
        className="w-[950px] h-[620px] bg-[#0a0a0a]/90 backdrop-blur-2xl border border-white/10 rounded-xl shadow-[0_25px_50px_-12px_rgba(0,0,0,0.5)] flex overflow-hidden ring-1 ring-white/5 relative z-10"
        style={{ willChange: 'opacity, transform, filter' }}
      >
        {/* Decor */}
        <div className="absolute top-0 right-0 w-[400px] h-[400px] bg-indigo-600/10 rounded-full blur-[100px] translate-x-1/2 -translate-y-1/2 pointer-events-none" />
        
        {/* Sidebar */}
        <motion.div 
          initial={animKey === 0 ? "hidden" : "visible"}
          animate={viewState}
          variants={sidebarVariants} 
          className="shrink-0 border-r border-white/5 bg-black/20 h-full"
        >
            <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} />
        </motion.div>

        {/* Content */}
        <motion.div variants={childVariants} className="flex-1 relative min-w-0 flex flex-col h-full">
            <SettingsPanel activeTab={activeTab} setImmersiveMode={setImmersiveMode} />
        </motion.div>

        {/* Immersive Music Overlay - Renders ON TOP of Sidebar and Content */}
        <AnimatePresence>
            {immersiveMode && (
                <motion.div 
                    initial={{ y: '100%' }}
                    animate={{ y: 0 }}
                    exit={{ y: '100%' }}
                    transition={{ type: 'spring', damping: 25, stiffness: 200 }}
                    className="absolute inset-0 z-[100] bg-neutral-900 flex flex-col"
                >
                     {/* Blurred Backdrop */}
                     <div className="absolute inset-0 z-0">
                         <img src="https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=400" className="w-full h-full object-cover opacity-30 blur-[100px]" />
                         <div className="absolute inset-0 bg-black/40" />
                     </div>

                     {/* Content */}
                     <div className="relative z-10 flex flex-col h-full p-8">
                         {/* Header */}
                         <div className="flex justify-between items-center text-white/50 mb-8">
                            <button onClick={() => setImmersiveMode(false)} className="hover:text-white hover:bg-white/10 p-2 rounded-full transition-all">
                                <ChevronDown size={32} />
                            </button>
                            <span className="text-xs font-bold tracking-widest uppercase bg-white/10 px-3 py-1 rounded-full text-white">Immersive Audio</span>
                         </div>

                         {/* Center Stage */}
                         <div className="flex-1 flex items-center justify-center gap-16 px-12">
                             {/* Big Art */}
                             <motion.div 
                                layoutId="mini-cover" 
                                className="w-[350px] aspect-square rounded-2xl overflow-hidden shadow-[0_30px_60px_-10px_rgba(0,0,0,0.6)] border border-white/10"
                             >
                                 <img src="https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=600" className="w-full h-full object-cover" />
                             </motion.div>

                             {/* Lyrics/Info */}
                             <div className="flex-1 max-w-md space-y-8">
                                 <div>
                                     <motion.h1 className="text-4xl font-bold text-white mb-2 leading-tight">Midnight City</motion.h1>
                                     <motion.p className="text-2xl text-indigo-300">M83</motion.p>
                                 </div>
                                 <div className="space-y-4 text-lg font-medium text-white/40 mask-image-linear-gradient h-[200px] overflow-hidden">
                                     <p className="text-white scale-105 origin-left">Waiting in a car</p>
                                     <p>Waiting for a ride in the dark</p>
                                     <p>The night city grows</p>
                                     <p>Look and see her eyes</p>
                                 </div>
                             </div>
                         </div>

                         {/* Footer Controls */}
                         <div className="mt-8 space-y-6 max-w-3xl mx-auto w-full">
                             {/* Progress */}
                             <div className="w-full h-1.5 bg-white/20 rounded-full cursor-pointer overflow-hidden">
                                 <div className="w-1/3 h-full bg-white shadow-[0_0_15px_white]" />
                             </div>
                             
                             <div className="flex items-center justify-between">
                                 <Shuffle size={24} className="text-white/40 hover:text-white cursor-pointer" />
                                 <div className="flex items-center gap-8">
                                     <SkipBack size={32} className="fill-white text-white hover:scale-110 transition-transform cursor-pointer" />
                                     <div className="w-20 h-20 rounded-full bg-white flex items-center justify-center text-black hover:scale-105 transition-transform cursor-pointer shadow-[0_0_30px_rgba(255,255,255,0.3)]">
                                         <Pause size={32} fill="currentColor" />
                                     </div>
                                     <SkipForward size={32} className="fill-white text-white hover:scale-110 transition-transform cursor-pointer" />
                                 </div>
                                 <div className="flex gap-6">
                                    <Heart size={24} className="text-indigo-400 hover:scale-110 transition-transform cursor-pointer" />
                                    <Repeat size={24} className="text-white/40 hover:text-white cursor-pointer" />
                                 </div>
                             </div>
                         </div>
                     </div>
                </motion.div>
            )}
        </AnimatePresence>

      </motion.div>
    </div>
  );
};

export default App;
