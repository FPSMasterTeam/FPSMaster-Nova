import React, { useEffect, useState } from 'react';
import { OobeView } from './components/OobeView';
import { MainMenuView } from './components/MainMenuView';
import { ClickGuiView } from './components/ClickGuiView';
import { NetworkManager } from './network/WebSocketClient';
import { PacketProcessor } from './network/PacketProcessor';
import { GuiLoadAckPacket, GuiLoadEventPacket } from './network/packets/GuiLoadPackets';
import { ModuleListPacket, RemoteModuleValueType } from './network/packets/ModulePackets';
import { useSetLocale, type Locale } from './i18n';

export type ViewState = 'hidden' | 'refresh' | 'visible' | 'closing';

export type ViewId = 'clickgui' | 'mainmenu' | 'oobe';
const KNOWN_VIEWS: readonly ViewId[] = ['clickgui', 'mainmenu', 'oobe'];

export interface ClickGuiConfig {
  enabled: boolean;
  backgroundEnabled: boolean;
  brandingVisible: boolean;
  animationsEnabled: boolean;
  developerMetrics: boolean;
  width: number;
  height: number;
}

const DEFAULT_CLICK_GUI_CONFIG: ClickGuiConfig = {
  enabled: true,
  backgroundEnabled: true,
  brandingVisible: true,
  animationsEnabled: true,
  developerMetrics: false,
  width: 950,
  height: 620,
};

// The webview no longer reloads to switch views; the game drives the view via the GuiLoadEvent's
// extraData (mode id). The URL path is only the seed for the very first render before any event.
const isKnownView = (value: string | null | undefined): value is ViewId =>
  value != null && (KNOWN_VIEWS as readonly string[]).includes(value);

const initialView = (): ViewId => {
  const path = window.location.pathname.replace(/^\/+|\/+$/g, '');
  const raw = path || new URLSearchParams(window.location.search).get('view') || 'clickgui';
  return isKnownView(raw) ? raw : 'clickgui';
};

const extractLocale = (packet: ModuleListPacket): Locale => {
  const settings = packet.modules.find((entry) => entry.id === 'client-settings');
  const language = settings?.values.find((value) => value.id === 'language')?.numberValue ?? 1;
  return language === 0 ? 'en' : 'zh';
};

const extractTheme = (packet: ModuleListPacket): 'dark' | 'light' => {
  const settings = packet.modules.find((entry) => entry.id === 'client-settings');
  const theme = settings?.values.find((value) => value.id === 'theme')?.numberValue ?? 0;
  return theme === 1 ? 'light' : 'dark';
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
        case 'background-enabled':
          nextConfig.backgroundEnabled = value.booleanValue;
          break;
        case 'branding-visible':
          nextConfig.brandingVisible = value.booleanValue;
          break;
        case 'animations-enabled':
          nextConfig.animationsEnabled = value.booleanValue;
          break;
        case 'developer-metrics':
          nextConfig.developerMetrics = value.booleanValue;
          break;
        default:
          break;
      }
      return;
    }

    if (value.type === RemoteModuleValueType.NUMBER) {
      // Guard against a missing / NaN / non-positive value: an invalid width/height would produce
      // an invalid CSS size (e.g. "NaNpx") and collapse the whole panel to nothing (UI disappears).
      const num = value.numberValue;
      const valid = typeof num === 'number' && Number.isFinite(num) && num > 0;
      switch (value.id) {
        case 'width':
          if (valid) nextConfig.width = num;
          break;
        case 'height':
          if (valid) nextConfig.height = num;
          break;
        default:
          break;
      }
    }
  });

  return nextConfig;
};

const App: React.FC = () => {
  const [view, setView] = useState<ViewId>(initialView);
  const [wsStatus, setWsStatus] = useState('idle');
  const [viewState, setViewState] = useState<ViewState>('visible');
  const [clickGuiConfig, setClickGuiConfig] = useState<ClickGuiConfig>(DEFAULT_CLICK_GUI_CONFIG);
  const [version, setVersion] = useState('');
  const setLocale = useSetLocale();

  useEffect(() => {
    NetworkManager.onStatusChange = (status) => {
      setWsStatus(status);
      console.info(`[WS] Status changed: ${status}`);
    };

    const handleGuiLoad = (packet: GuiLoadEventPacket) => {
      console.info(`[WS] Received GuiLoadEvent: ${packet.eventType} (${packet.extraData ?? '-'})`);

      if (packet.eventType === 'close') {
        setViewState('closing');
        setTimeout(() => {
          NetworkManager.send(new GuiLoadAckPacket(true, 'GUI close event received', Date.now()));
          console.info('[WS] sent GuiCloseAck after closing animation');
        }, clickGuiConfig.animationsEnabled ? 250 : 0);
        return;
      }

      // Open: switch view client-side (no page reload) from the requested mode id, then replay the
      // entrance animation. The freshly mounted view resets its own state naturally.
      setView((current) => (isKnownView(packet.extraData) ? packet.extraData : current));
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
      setLocale(extractLocale(packet));
      if (packet.version) {
        setVersion(packet.version);
      }
      // Apply the client theme (dark/light) globally to every webview view.
      document.documentElement.dataset.theme = extractTheme(packet);
    };

    PacketProcessor.register(9, handleGuiLoad);
    PacketProcessor.register(12, handleModuleList);
    NetworkManager.connect();

    return () => {
      PacketProcessor.unregister(9, handleGuiLoad);
      PacketProcessor.unregister(12, handleModuleList);
    };
  }, [clickGuiConfig.animationsEnabled, setLocale]);

  // Persistent shell: the WebSocket connection and the open/close handshake live here, while only the
  // active view subtree is mounted. Switching views remounts the target view (fresh state, animations
  // reset) without ever reloading the page.
  if (view === 'oobe') {
    return <OobeView wsStatus={wsStatus} />;
  }
  if (view === 'mainmenu') {
    return <MainMenuView wsStatus={wsStatus} version={version} />;
  }
  return <ClickGuiView viewState={viewState} wsStatus={wsStatus} clickGuiConfig={clickGuiConfig} version={version} />;
};

export default App;
