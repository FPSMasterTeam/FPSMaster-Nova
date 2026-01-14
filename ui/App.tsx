import React, { useEffect, useRef, useState } from 'react';
import { Sidebar } from './components/Sidebar';
import { SettingsPanel } from './components/SettingsPanel';
import { TabId } from './types';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronDown, SkipBack, Play, Pause, SkipForward, Repeat, Shuffle, Heart } from 'lucide-react';
import { NetworkManager } from './network/WebSocketClient';
import { PacketProcessor } from './network/PacketProcessor';
import { GuiLoadAckPacket, GuiLoadEventPacket } from './network/packets/GuiLoadPackets';

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabId>(TabId.OPTIMIZE);
  const [immersiveMode, setImmersiveMode] = useState(false);
  const [wsStatus, setWsStatus] = useState('idle');
  const [animKey, setAnimKey] = useState(0);
  const [viewState, setViewState] = useState<'visible' | 'closing'>('visible');

  useEffect(() => {
    // Setup Network Manager
    NetworkManager.onStatusChange = (status) => {
      setWsStatus(status);
      console.info(`[WS] Status changed: ${status}`);
    };

    // Register Packet Handlers
    const handleGuiLoad = (packet: GuiLoadEventPacket) => {
      console.info(`[WS] Received GuiLoadEvent: ${packet.eventType}`);
      
      if (packet.eventType === 'close') {
        setViewState('closing');
        setTimeout(() => {
          NetworkManager.send(new GuiLoadAckPacket(true, 'GUI close event received', Date.now()));
          console.info('[WS] sent GuiCloseAck after closing animation');
        }, 250);
      } else {
        // open/refresh
        setViewState('refresh');
        requestAnimationFrame(() => {
          setViewState('visible');
          requestAnimationFrame(() => {
            NetworkManager.send(new GuiLoadAckPacket(true, 'GUI load event received', Date.now()));
            console.info('[WS] sent GuiLoadAck (after anim prep)');
          });
        });
      }
    };

    PacketProcessor.register(9, handleGuiLoad);
    NetworkManager.connect();

    return () => {
      PacketProcessor.unregister(9, handleGuiLoad);
    };
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
            <SettingsPanel activeTab={activeTab} immersiveMode={immersiveMode} setImmersiveMode={setImmersiveMode} />
        </motion.div>

      </motion.div>
    </div>
  );
};

export default App;
