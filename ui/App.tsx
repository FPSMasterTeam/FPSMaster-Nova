import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Sidebar } from './components/Sidebar';
import { SettingsPanel } from './components/SettingsPanel';
import { TabId } from './types';
import { NetworkManager } from './network/WebSocketClient';
import { PacketProcessor } from './network/PacketProcessor';
import { GuiLoadAckPacket, GuiLoadEventPacket } from './network/packets/GuiLoadPackets';
import { ModuleListPacket, RemoteModuleValueType } from './network/packets/ModulePackets';

type ViewState = 'hidden' | 'refresh' | 'visible' | 'closing';

interface ClickGuiConfig {
  enabled: boolean;
  backgroundEnabled: boolean;
  backgroundBlur: boolean;
  brandingVisible: boolean;
  animationsEnabled: boolean;
  width: number;
  height: number;
}

const DEFAULT_CLICK_GUI_CONFIG: ClickGuiConfig = {
  enabled: true,
  backgroundEnabled: true,
  backgroundBlur: true,
  brandingVisible: true,
  animationsEnabled: true,
  width: 950,
  height: 620,
};

const extractClickGuiConfig = (packet: ModuleListPacket): ClickGuiConfig => {
  const module = packet.modules.find((entry) => entry.id === 'clickgui');
  if (!module) {
    return DEFAULT_CLICK_GUI_CONFIG;
  }

  const nextConfig: ClickGuiConfig = {
    ...DEFAULT_CLICK_GUI_CONFIG,
    enabled: module.enabled,
  };

  module.values.forEach((value) => {
    if (value.type === RemoteModuleValueType.BOOLEAN) {
      switch (value.id) {
        case 'background_enabled':
          nextConfig.backgroundEnabled = value.booleanValue;
          break;
        case 'background_blur':
          nextConfig.backgroundBlur = value.booleanValue;
          break;
        case 'branding_visible':
          nextConfig.brandingVisible = value.booleanValue;
          break;
        case 'animations_enabled':
          nextConfig.animationsEnabled = value.booleanValue;
          break;
        default:
          break;
      }
      return;
    }

    if (value.type === RemoteModuleValueType.NUMBER) {
      switch (value.id) {
        case 'width':
          nextConfig.width = value.numberValue;
          break;
        case 'height':
          nextConfig.height = value.numberValue;
          break;
        default:
          break;
      }
    }
  });

  return nextConfig;
};

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabId>(TabId.OPTIMIZE);
  const [immersiveMode, setImmersiveMode] = useState(false);
  const [wsStatus, setWsStatus] = useState('idle');
  const [viewState, setViewState] = useState<ViewState>('visible');
  const [clickGuiConfig, setClickGuiConfig] = useState<ClickGuiConfig>(DEFAULT_CLICK_GUI_CONFIG);

  useEffect(() => {
    NetworkManager.onStatusChange = (status) => {
      setWsStatus(status);
      console.info(`[WS] Status changed: ${status}`);
    };

    const handleGuiLoad = (packet: GuiLoadEventPacket) => {
      console.info(`[WS] Received GuiLoadEvent: ${packet.eventType}`);

      if (packet.eventType === 'close') {
        setViewState('closing');
        setTimeout(() => {
          NetworkManager.send(new GuiLoadAckPacket(true, 'GUI close event received', Date.now()));
          console.info('[WS] sent GuiCloseAck after closing animation');
        }, clickGuiConfig.animationsEnabled ? 250 : 0);
        return;
      }

      setViewState('refresh');
      requestAnimationFrame(() => {
        setViewState('visible');
        requestAnimationFrame(() => {
          NetworkManager.send(new GuiLoadAckPacket(true, 'GUI load event received', Date.now()));
          console.info('[WS] sent GuiLoadAck (after anim prep)');
        });
      });
    };

    const handleModuleList = (packet: ModuleListPacket) => {
      setClickGuiConfig(extractClickGuiConfig(packet));
    };

    PacketProcessor.register(9, handleGuiLoad);
    PacketProcessor.register(12, handleModuleList);
    NetworkManager.connect();

    return () => {
      PacketProcessor.unregister(9, handleGuiLoad);
      PacketProcessor.unregister(12, handleModuleList);
    };
  }, [clickGuiConfig.animationsEnabled]);

  const animationEnabled = clickGuiConfig.enabled && clickGuiConfig.animationsEnabled;
  const showBackground = clickGuiConfig.enabled && clickGuiConfig.backgroundEnabled;
  const showBranding = clickGuiConfig.enabled && clickGuiConfig.brandingVisible;
  const blurEnabled = clickGuiConfig.enabled && clickGuiConfig.backgroundBlur;
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

  return (
    <div className="fixed inset-0 flex items-center justify-center font-sans antialiased selection:bg-indigo-500/30 text-slate-200 p-4">
      <motion.div
        initial="hidden"
        animate={viewState}
        variants={overlayVariants}
        className={`fixed inset-0 bg-black ${blurEnabled ? 'backdrop-blur-sm' : ''}`}
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
      ) : null}

      <motion.div
        initial="hidden"
        animate={viewState}
        variants={containerVariants}
        className={`bg-[#0a0a0a]/90 border border-white/10 rounded-xl shadow-[0_25px_50px_-12px_rgba(0,0,0,0.5)] flex overflow-hidden ring-1 ring-white/5 relative z-10 ${blurEnabled ? 'backdrop-blur-2xl' : ''}`}
        style={{
          width: `min(calc(100vw - 2rem), ${clickGuiConfig.width}px)`,
          height: `min(calc(100vh - 2rem), ${clickGuiConfig.height}px)`,
          willChange: 'opacity, transform, filter',
        }}
      >
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
    </div>
  );
};

export default App;
