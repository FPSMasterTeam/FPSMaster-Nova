import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Activity, Monitor, Shield } from 'lucide-react';
import { NetworkManager } from '../network/WebSocketClient';
import { PacketProcessor } from '../network/PacketProcessor';
import { GuiLoadAckPacket } from '../network/packets/GuiLoadPackets';
import {
  ClientConfigPacket,
  ClientConfigRequestPacket,
  ClientConfigUpdatePacket,
} from '../network/packets/ClientConfigPackets';
import { Toggle, CustomSelect } from './Controls';
import { useT } from '../i18n';

interface OobeViewProps {
  wsStatus: string;
}

const BACKGROUND_OPTIONS = ['panorama_1', 'panorama_2', 'panorama_3', 'classic', 'shader', 'custom'];

const cloneClientConfig = (config: ClientConfigPacket): ClientConfigPacket => {
  const next = new ClientConfigPacket();
  next.musicVolume = config.musicVolume;
  next.anonymousDataEnabled = config.anonymousDataEnabled;
  next.telemetryInstanceId = config.telemetryInstanceId;
  next.background = config.background;
  next.oobeCompleted = config.oobeCompleted;
  next.antiCheatEnabled = config.antiCheatEnabled;
  next.classicBackgroundColor = config.classicBackgroundColor;
  next.classicBackgroundHue = config.classicBackgroundHue;
  next.classicBackgroundSaturation = config.classicBackgroundSaturation;
  next.classicBackgroundBrightness = config.classicBackgroundBrightness;
  next.classicBackgroundAlpha = config.classicBackgroundAlpha;
  next.classicBackgroundMode = config.classicBackgroundMode;
  return next;
};

export const OobeView: React.FC<OobeViewProps> = ({ wsStatus }) => {
  const t = useT();
  const [clientConfig, setClientConfig] = useState<ClientConfigPacket>(() => new ClientConfigPacket());

  useEffect(() => {
    const handleClientConfig = (packet: ClientConfigPacket) => {
      setClientConfig(packet);
    };

    PacketProcessor.register(16, handleClientConfig);
    if (wsStatus === 'open') {
      NetworkManager.send(new ClientConfigRequestPacket());
    }

    return () => {
      PacketProcessor.unregister(16, handleClientConfig);
    };
  }, [wsStatus]);

  const updatePreference = (changes: Partial<ClientConfigPacket>) => {
    const next = cloneClientConfig(clientConfig);
    Object.assign(next, changes);
    setClientConfig(next);

    if (wsStatus === 'open') {
      NetworkManager.send(new ClientConfigUpdatePacket(next.musicVolume, {
        updateMusicVolume: false,
        anonymousDataEnabled: next.anonymousDataEnabled,
        clientPreferences: next,
      }));
    }
  };

  const finish = (openSettings: boolean) => {
    const next = cloneClientConfig(clientConfig);
    next.oobeCompleted = true;
    setClientConfig(next);

    if (wsStatus === 'open') {
      NetworkManager.send(new ClientConfigUpdatePacket(next.musicVolume, {
        updateMusicVolume: false,
        anonymousDataEnabled: next.anonymousDataEnabled,
        clientPreferences: next,
      }));
      NetworkManager.send(new GuiLoadAckPacket(true, openSettings ? 'oobe:settings' : 'oobe:title', Date.now()));
    }
  };

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/40 px-6 text-neutral-200">
      <motion.div
        initial={{ opacity: 0, y: 16, scale: 0.98 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
        className="fps-glass w-full max-w-4xl overflow-hidden rounded-[18px]"
      >
        <div className="flex items-center gap-3 border-b border-white/10 px-6 py-5">
          <div className="grid h-9 w-9 place-items-center rounded-[10px] bg-gradient-to-br from-accent to-[#3a43b8] text-[15px] font-black text-white">
            N
          </div>
          <div>
            <h1 className="text-[16px] font-bold tracking-tight text-white">FPSMaster Nova</h1>
            <p className="mt-0.5 text-xs text-neutral-400">{t('oobe.subtitle')}</p>
          </div>
        </div>

        <div className="grid gap-3 px-6 py-5 md:grid-cols-3">
          <section className="rounded-xl border border-white/10 bg-white/[0.04] p-4">
            <div className="mb-3 grid h-8 w-8 place-items-center rounded-[10px] bg-accent/15 text-accent-text">
              <Activity size={15} strokeWidth={1.8} />
            </div>
            <div className="text-[13.5px] font-semibold text-white">{t('oobe.anonymous.title')}</div>
            <p className="mb-4 mt-1.5 min-h-10 text-[11.5px] leading-relaxed text-neutral-400">
              {t('oobe.anonymous.desc')}
            </p>
            <div className="flex items-center justify-between">
              <span className="text-xs text-neutral-400">{t('oobe.anonymous.toggle')}</span>
              <Toggle
                checked={clientConfig.anonymousDataEnabled}
                onChange={(value) => updatePreference({ anonymousDataEnabled: value })}
              />
            </div>
          </section>

          <section className="rounded-xl border border-white/10 bg-white/[0.04] p-4">
            <div className="mb-3 grid h-8 w-8 place-items-center rounded-[10px] bg-accent/15 text-accent-text">
              <Shield size={15} strokeWidth={1.8} />
            </div>
            <div className="text-[13.5px] font-semibold text-white">{t('oobe.safety.title')}</div>
            <p className="mb-4 mt-1.5 min-h-10 text-[11.5px] leading-relaxed text-neutral-400">
              {t('oobe.safety.desc')}
            </p>
            <div className="flex items-center justify-between">
              <span className="text-xs text-neutral-400">{t('oobe.safety.toggle')}</span>
              <Toggle
                checked={clientConfig.antiCheatEnabled}
                onChange={(value) => updatePreference({ antiCheatEnabled: value })}
              />
            </div>
          </section>

          <section className="rounded-xl border border-white/10 bg-white/[0.04] p-4">
            <div className="mb-3 grid h-8 w-8 place-items-center rounded-[10px] bg-accent/15 text-accent-text">
              <Monitor size={15} strokeWidth={1.8} />
            </div>
            <div className="text-[13.5px] font-semibold text-white">{t('oobe.background.title')}</div>
            <p className="mb-4 mt-1.5 min-h-10 text-[11.5px] leading-relaxed text-neutral-400">
              {t('oobe.background.desc')}
            </p>
            <CustomSelect
              value={clientConfig.background}
              options={BACKGROUND_OPTIONS.map((id) => ({ value: id, label: t(`bg.${id}`) }))}
              onChange={(value) => updatePreference({ background: String(value) })}
            />
          </section>
        </div>

        <div className="flex items-center justify-between gap-4 border-t border-white/10 bg-black/20 px-6 py-4">
          <div className="flex items-center gap-2 text-xs text-neutral-400">
            <span
              className={`h-1.5 w-1.5 rounded-full transition-colors ${
                wsStatus === 'open' ? 'bg-emerald-400' : 'animate-pulse bg-amber-400'
              }`}
            />
            {wsStatus === 'open' ? t('oobe.status.ready') : t('oobe.status.connecting')}
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => finish(false)}
              disabled={wsStatus !== 'open'}
              className="rounded-[10px] px-4 py-2 text-[13px] font-medium text-neutral-400 transition-colors hover:bg-white/10 hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
            >
              {t('oobe.finish')}
            </button>
            <button
              type="button"
              onClick={() => finish(true)}
              disabled={wsStatus !== 'open'}
              className="rounded-[10px] bg-accent px-4 py-2 text-[13px] font-semibold text-white transition-colors hover:bg-accent-hover disabled:cursor-not-allowed disabled:bg-white/10 disabled:text-neutral-500"
            >
              {t('oobe.enterSettings')}
            </button>
          </div>
        </div>
      </motion.div>
    </div>
  );
};
