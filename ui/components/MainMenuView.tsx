import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { Play, Globe, LayoutGrid, SlidersHorizontal, Power, AppWindow, type LucideIcon } from 'lucide-react';
import { useT } from '../i18n';
import { NetworkManager } from '../network/WebSocketClient';
import { PacketProcessor } from '../network/PacketProcessor';
import { UIEventPacket } from '../network/packets/UIEventPacket';
import { ModuleListRequestPacket } from '../network/packets/ModulePackets';
import { ClientConfigPacket, ClientConfigRequestPacket, ClientConfigUpdatePacket } from '../network/packets/ClientConfigPackets';
import { MainMenuBackground, resolveMenuBgVariant } from './MainMenuBackground';
import { MenuBackgroundPicker } from './MenuBackgroundPicker';

interface MainMenuViewProps {
  wsStatus: string;
  version: string;
}

interface MenuItem {
  key: string;
  event: string;
  icon: LucideIcon;
  primary?: boolean;
  danger?: boolean;
}

const ITEMS: MenuItem[] = [
  { key: 'menu.singleplayer', event: 'open-singleplayer', icon: Play, primary: true },
  { key: 'menu.multiplayer', event: 'open-multiplayer', icon: Globe },
  { key: 'menu.clickgui', event: 'open-clickgui', icon: LayoutGrid },
  { key: 'menu.options', event: 'open-options', icon: SlidersHorizontal },
  { key: 'nav.nativeUi', event: 'use-native-ui', icon: AppWindow },
  { key: 'menu.quit', event: 'quit-game', icon: Power, danger: true },
];

export const MainMenuView: React.FC<MainMenuViewProps> = ({ wsStatus, version }) => {
  const t = useT();
  const [clientConfig, setClientConfig] = useState<ClientConfigPacket | null>(null);

  useEffect(() => {
    if (wsStatus === 'open') {
      NetworkManager.send(new ModuleListRequestPacket());
      NetworkManager.send(new ClientConfigRequestPacket());
    }
  }, [wsStatus]);

  useEffect(() => {
    const handler = (packet: ClientConfigPacket) => setClientConfig(packet);
    PacketProcessor.register(16, handler);
    return () => PacketProcessor.unregister(16, handler);
  }, []);

  const greeting = useMemo(() => {
    const hour = new Date().getHours();
    if (hour < 12) return t('menu.greeting.morning');
    if (hour < 18) return t('menu.greeting.afternoon');
    return t('menu.greeting.evening');
  }, [t]);

  const background = clientConfig?.background || 'panorama_1';
  const bgVariant = resolveMenuBgVariant(background);

  const changeBackground = (id: string) => {
    if (wsStatus !== 'open' || !clientConfig) return;
    const nextPrefs = { ...clientConfig, background: id };
    NetworkManager.send(
      new ClientConfigUpdatePacket(clientConfig.musicVolume, {
        updateMusicVolume: false,
        clientPreferences: nextPrefs,
      }),
    );
    const next = new ClientConfigPacket();
    Object.assign(next, clientConfig, { background: id });
    setClientConfig(next);
  };

  const fire = (event: string) => {
    if (wsStatus !== 'open') return;
    NetworkManager.send(new UIEventPacket(event));
  };

  return (
    <div className="relative h-screen w-screen overflow-hidden text-white">
      {bgVariant && <MainMenuBackground variant={bgVariant} />}

      <MenuBackgroundPicker current={background} onChange={changeBackground} disabled={wsStatus !== 'open' || !clientConfig} />

      <div className="pointer-events-none absolute inset-0">
        <div className="absolute inset-y-0 left-0 w-[min(46vw,420px)] bg-gradient-to-r from-black/75 via-black/40 to-transparent" />
      </div>

      <div className="relative z-10 flex h-full w-full max-w-xl flex-col px-12 py-10">
        <div className="scrollbar-hide flex min-h-0 flex-1 flex-col justify-center gap-8 overflow-y-auto overflow-x-hidden py-2">
          <motion.div
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.45, ease: 'easeOut' }}
            className="flex shrink-0 flex-col"
          >
            <p className="text-[13px] font-medium text-neutral-400">{greeting}</p>
            <h1 className="mt-2.5 text-[56px] font-black leading-none tracking-tight text-white">Nova</h1>
            <div className="mt-2.5 flex items-center gap-2.5 text-[12px] font-semibold uppercase tracking-[0.38em] text-accent-text">
              FPSMaster
              <span className="h-px w-12 bg-gradient-to-r from-accent to-transparent" />
            </div>
            <p className="mt-2.5 max-w-[280px] text-[13px] leading-relaxed text-neutral-400">{t('menu.tagline')}</p>
          </motion.div>

          <div className="flex w-[280px] shrink-0 flex-col gap-2">
            {ITEMS.map((item, i) => {
              const Icon = item.icon;
              return (
                <motion.button
                  key={item.key}
                  type="button"
                  onClick={() => fire(item.event)}
                  disabled={wsStatus !== 'open'}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ duration: 0.22, delay: 0.04 + i * 0.04, ease: 'easeOut' }}
                  className={`menu-tile group flex h-[46px] items-center gap-3 rounded-[12px] border px-4 text-left text-[14px] font-medium backdrop-blur-md transition-all duration-150 ease-out hover:translate-x-1 disabled:cursor-not-allowed disabled:opacity-40 ${
                    item.primary
                      ? 'border-accent/35 bg-accent/12 hover:bg-accent/20'
                      : item.danger
                        ? 'menu-tile danger border-white/10 bg-black/45 text-neutral-300 hover:border-red-400/30 hover:bg-red-500/10 hover:text-red-300'
                        : 'border-white/10 bg-black/45 hover:border-white/20 hover:bg-neutral-900/90'
                  }`}
                >
                  <Icon
                    size={18}
                    strokeWidth={1.8}
                    className={`shrink-0 transition-colors ${
                      item.primary
                        ? 'text-accent-text'
                        : item.danger
                          ? 'text-neutral-500 group-hover:text-red-300'
                          : 'text-neutral-400 group-hover:text-accent-text'
                    }`}
                  />
                  <span>{t(item.key)}</span>
                </motion.button>
              );
            })}
          </div>
        </div>

        <div className="shrink-0 pt-4 text-[11px] tracking-widest text-neutral-600">
          FPSMaster Nova{version ? ` · ${version}` : ''}
        </div>
      </div>
    </div>
  );
};
