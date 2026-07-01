import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Activity, Monitor, Shield, Zap } from 'lucide-react';
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

const BACKGROUND_OPTIONS = [
  { id: 'panorama_1', label: '全景 I' },
  { id: 'panorama_2', label: '全景 II' },
  { id: 'panorama_3', label: '全景 III' },
  { id: 'classic', label: '纯色' },
  { id: 'shader', label: '动态' },
  { id: 'custom', label: '自定义' },
];

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
    <div className="fixed inset-0 flex items-center justify-center bg-[#050505] px-6 text-slate-200 selection:bg-indigo-500/30">
      <motion.div
        initial={{ opacity: 0, y: 18, scale: 0.98 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
        className="w-full max-w-4xl overflow-hidden rounded-xl border border-white/10 bg-[#0a0a0a]/95 shadow-[0_25px_60px_rgba(0,0,0,0.55)] ring-1 ring-white/5"
      >
        <div className="border-b border-white/10 px-7 py-6">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-lg border border-indigo-400/20 bg-indigo-400/10 text-indigo-200">
              <Zap size={18} />
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight text-white">FPSMaster Nova</h1>
              <p className="mt-1 text-xs text-neutral-500">{t('oobe.subtitle')}</p>
            </div>
          </div>
        </div>

        <div className="grid gap-4 px-7 py-6 md:grid-cols-3">
          <section className="rounded-lg border border-white/5 bg-white/[0.03] p-4">
            <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-white">
              <Activity size={16} className="text-indigo-300" />
              {t('oobe.anonymous.title')}
            </div>
            <p className="mb-4 min-h-12 text-xs leading-5 text-neutral-500">
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

          <section className="rounded-lg border border-white/5 bg-white/[0.03] p-4">
            <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-white">
              <Shield size={16} className="text-indigo-300" />
              {t('oobe.safety.title')}
            </div>
            <p className="mb-4 min-h-12 text-xs leading-5 text-neutral-500">
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

          <section className="rounded-lg border border-white/5 bg-white/[0.03] p-4">
            <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-white">
              <Monitor size={16} className="text-indigo-300" />
              {t('oobe.background.title')}
            </div>
            <p className="mb-4 min-h-12 text-xs leading-5 text-neutral-500">
              {t('oobe.background.desc')}
            </p>
            <CustomSelect
              value={clientConfig.background}
              options={BACKGROUND_OPTIONS.map((option) => ({ value: option.id, label: t(`bg.${option.id}`) }))}
              onChange={(value) => updatePreference({ background: String(value) })}
            />
          </section>
        </div>

        <div className="flex items-center justify-between gap-4 border-t border-white/10 px-7 py-5">
          <div className="text-xs text-neutral-500">
            WebSocket: <span className="font-mono text-neutral-300">{wsStatus}</span>
          </div>
          <div className="flex gap-3">
            <button
              type="button"
              onClick={() => finish(false)}
              disabled={wsStatus !== 'open'}
              className="rounded-lg border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold text-neutral-200 transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {t('oobe.finish')}
            </button>
            <button
              type="button"
              onClick={() => finish(true)}
              disabled={wsStatus !== 'open'}
              className="rounded-lg bg-indigo-500 px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-indigo-400 disabled:cursor-not-allowed disabled:bg-white/10 disabled:text-neutral-500"
            >
              {t('oobe.enterSettings')}
            </button>
          </div>
        </div>
      </motion.div>
    </div>
  );
};
