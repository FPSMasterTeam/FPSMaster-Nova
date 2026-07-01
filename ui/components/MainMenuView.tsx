import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Play, Globe, LayoutGrid, SlidersHorizontal, Power, Zap, type LucideIcon } from 'lucide-react';
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
  { key: 'menu.quit', event: 'quit-game', icon: Power, danger: true },
];

export const MainMenuView: React.FC<MainMenuViewProps> = ({ wsStatus }) => {
  const t = useT();
  const [clientConfig, setClientConfig] = useState<ClientConfigPacket | null>(null);

  // Sync locale (App's ModuleList handler calls setLocale) + fetch the menu background once connected.
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

  const background = clientConfig?.background || 'panorama_1';
  // Webview-rendered background variant, or null when the Minecraft side owns it (panorama/classic/shader).
  const bgVariant = resolveMenuBgVariant(background);

  const changeBackground = (id: string) => {
    if (wsStatus !== 'open' || !clientConfig) return;
    // Preserve all other client preferences; only the background changes.
    const nextPrefs = { ...clientConfig, background: id };
    NetworkManager.send(
      new ClientConfigUpdatePacket(clientConfig.musicVolume, {
        updateMusicVolume: false,
        clientPreferences: nextPrefs,
      }),
    );
    // Optimistic local update so the menu repaints immediately.
    const next = new ClientConfigPacket();
    Object.assign(next, clientConfig, { background: id });
    setClientConfig(next);
  };

  const fire = (event: string) => {
    if (wsStatus !== 'open') return;
    NetworkManager.send(new UIEventPacket(event));
  };

  return (
    // Transparent so the Minecraft-rendered menu background (the "menu background" setting) shows through.
    <div className="relative h-screen w-screen overflow-hidden text-white">
      {/* Webview-rendered background (aurora / constellation / synthwave / custom). */}
      {bgVariant && <MainMenuBackground variant={bgVariant} />}

      {/* Background style picker (top-right, expandable). */}
      <MenuBackgroundPicker current={background} onChange={changeBackground} disabled={wsStatus !== 'open' || !clientConfig} />

      {/* Left readability scrim + a subtle ambient accent (only over the transparent/MC background). */}
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute inset-0 bg-gradient-to-r from-black/75 via-black/35 to-transparent" />
        {!bgVariant && (
          <motion.div
            className="absolute -left-40 -top-40 h-[520px] w-[520px] rounded-full bg-indigo-600/15 blur-[120px]"
            animate={{ x: [0, 40, 0], y: [0, 30, 0] }}
            transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }}
          />
        )}
      </div>

      {/* Full-height column: brand + menu are vertically centered and scroll if the window is short;
          the footer stays in flow at the bottom so it never overlaps the last button. */}
      <div className="relative z-10 flex h-full w-full max-w-xl flex-col px-16 py-8">
        <div className="flex min-h-0 flex-1 flex-col justify-center gap-8 overflow-y-auto overflow-x-hidden py-2">
          <motion.div
            initial={{ opacity: 0, x: -24 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.5, ease: 'easeOut' }}
            className="flex shrink-0 flex-col gap-2"
          >
            <div className="flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-500/15 ring-1 ring-indigo-400/30">
                <Zap size={22} className="text-indigo-300" />
              </div>
              <div className="text-[11px] font-bold uppercase tracking-[0.35em] text-neutral-400">FPSMaster</div>
            </div>
            <h1 className="text-5xl font-black tracking-tight text-white">
              Nova
            </h1>
            <p className="text-sm text-neutral-400">{t('menu.tagline')}</p>
          </motion.div>

          <div className="flex shrink-0 flex-col gap-2.5">
            {ITEMS.map((item, i) => {
              const Icon = item.icon;
              return (
                <motion.button
                  key={item.key}
                  type="button"
                  onClick={() => fire(item.event)}
                  disabled={wsStatus !== 'open'}
                  initial={{ opacity: 0, x: -18 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ duration: 0.35, delay: 0.08 + i * 0.06, ease: 'easeOut' }}
                  whileHover={{ x: 6 }}
                  className={`group flex items-center gap-4 rounded-xl border px-5 py-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${
                    item.primary
                      ? 'border-indigo-400/40 bg-indigo-500/15 hover:bg-indigo-500/25'
                      : item.danger
                        ? 'border-white/5 bg-white/[0.03] hover:border-red-400/30 hover:bg-red-500/10'
                        : 'border-white/5 bg-white/[0.03] hover:border-white/15 hover:bg-white/[0.06]'
                  }`}
                >
                  <div
                    className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg transition-colors ${
                      item.primary
                        ? 'bg-indigo-500/20 text-indigo-200'
                        : item.danger
                          ? 'bg-white/5 text-neutral-400 group-hover:bg-red-500/15 group-hover:text-red-200'
                          : 'bg-white/5 text-neutral-300'
                    }`}
                  >
                    <Icon size={18} />
                  </div>
                  <span className="text-sm font-semibold tracking-wide">{t(item.key)}</span>
                </motion.button>
              );
            })}
          </div>
        </div>

        {/* Footer (in flow at the bottom of the column) */}
        <div className="flex shrink-0 items-center gap-2 pt-4 text-[10px] uppercase tracking-widest text-neutral-600">
          <span className={`h-1.5 w-1.5 rounded-full ${wsStatus === 'open' ? 'bg-emerald-400' : 'bg-neutral-600'}`} />
          FPSMaster Nova · v4.0.0
        </div>
      </div>
    </div>
  );
};
