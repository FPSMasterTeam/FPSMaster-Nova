import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Sparkles, Shield, Database, Box, Zap, Eye, Activity, Clock3, Layout, AlertTriangle } from 'lucide-react';
import { TabId, ConfigType, FeatureModule, ConfigItem, CategoryData, ConfigValue } from '../types';
import { Toggle, Checkbox, Slider, FeatureCard, SelectBox, KeybindInput, ColorPicker } from './Controls';
import { MusicPlayer } from './MusicPlayer';
import { NetworkManager } from '../network/WebSocketClient';
import { PacketProcessor } from '../network/PacketProcessor';
import {
  ModuleListPacket,
  ModuleListRequestPacket,
  ModuleTogglePacket,
  ModuleValueUpdatePacket,
  RemoteModuleEntry,
  RemoteModuleValueEntry,
  RemoteModuleValueType,
} from '../network/packets/ModulePackets';
import {
  ConfigProfileActionPacket,
  ConfigProfilesPacket,
  ConfigProfilesRequestPacket,
} from '../network/packets/ConfigProfilePackets';
import {
  ClientConfigPacket,
  ClientConfigRequestPacket,
  ClientConfigUpdatePacket,
} from '../network/packets/ClientConfigPackets';

interface SettingsPanelProps {
  activeTab: TabId;
  immersiveMode: boolean;
  setImmersiveMode: (v: boolean) => void;
  wsStatus: string;
}

const SYNCED_TAB_TITLES: Record<TabId, string> = {
  [TabId.OPTIMIZE]: '性能优化',
  [TabId.RENDER]: '视觉渲染',
  [TabId.TOOLS]: '实用工具',
  [TabId.INTERFACE]: '界面功能',
  [TabId.MUSIC]: '音乐',
  [TabId.SETTINGS]: '设置',
};

const CATEGORY_TO_TAB: Record<string, TabId> = {
  OPTIMIZATION: TabId.OPTIMIZE,
  RENDER: TabId.RENDER,
  AUXILIARY: TabId.TOOLS,
  UI: TabId.INTERFACE,
};

const MODULE_METADATA: Record<string, { title: string; description: string; icon: typeof Box }> = {
  'optimization': { title: '性能优化', description: '核心性能调整', icon: Zap },
  'no-hurt-cam': { title: '无受伤抖动', description: '移除受伤时的镜头晃动', icon: Eye },
  'no-hit-delay': { title: '无攻击延迟', description: '清除挥空后的攻击冷却', icon: Activity },
  'better-fishing-rod': { title: '钓鱼竿优化', description: '允许调整鱼线渲染宽度', icon: Box },
  'full-bright': { title: '保持亮度', description: '永久夜视效果', icon: Eye },
  'clickgui': { title: 'ClickGUI', description: '浏览器界面表现设置', icon: Layout },
  'client-settings': { title: '客户端设置', description: '全局客户端行为和界面缩放', icon: Layout },
  'hud-editor': { title: 'HUD 编辑器', description: '打开基础 HUD 布局编辑界面', icon: Layout },
  'sprint': { title: '强制疾跑', description: '自动保持疾跑状态', icon: Activity },
  'time-changer': { title: '时间修改', description: '修改世界时间', icon: Clock3 },
  'custom-fov': { title: '自定义 FOV', description: '分别禁用速度/飞行/拉弓 FOV 变化', icon: Eye },
  'name-protect': { title: '名称保护', description: '替换玩家列表中的名称显示', icon: Shield },
  'minimized-bobbing': { title: '最小摇晃', description: '禁用画面摇晃', icon: Shield },
  'animation': { title: '动画', description: '修改游戏内动画', icon: Shield }
};

const VALUE_METADATA: Record<string, Partial<ConfigItem> & { label: string }> = {
  'optimization.ignore-armor-stand': { label: '忽略盔甲架' },
  'optimization.entity-culling': { label: '实体渲染优化' },
  'optimization.fast-load': { label: '快速加载' },
  'optimization.entity-limitation': { label: '实体限制', suffix: ' individual' },
  'optimization.fps-losing-focus': { label: '失焦 FPS 限制', suffix: ' FPS' },
  'optimization.particle-limitation': { label: '粒子限制' },
  'optimization.font-optimization': { label: '字体优化' },
  'optimization.static-particle-color': { label: '静态粒子颜色' },
  'optimization.chunk-loading-limitation': { label: '限制区块加载' },
  'optimization.chunk-updating-limitation': { label: '区块更新限制', suffix: 'ms' },
  'better-fishing-rod.string-width': { label: '鱼线宽度' },
  'time-changer.time': { label: '时间' },
  'custom-fov.no-speed-fov': { label: '禁用速度 FOV' },
  'custom-fov.no-fly-fov': { label: '禁用飞行 FOV' },
  'custom-fov.no-bow-fov': { label: '禁用拉弓 FOV' },
  'name-protect.name': { label: '目标名称', placeholder: '留空则使用当前玩家' },
  'name-protect.replacement': { label: '替换文本', placeholder: 'Hide' },
  'clickgui.background-enabled': { label: '背景遮罩' },
  'clickgui.background-blur': { label: '背景模糊' },
  'clickgui.branding-visible': { label: '显示角标' },
  'clickgui.animations-enabled': { label: '界面动画' },
  'clickgui.developer-metrics': { label: '开发指标' },
  'clickgui.hardware-acceleration': { label: '硬件加速' },
  'clickgui.width': { label: '窗口宽度', suffix: 'px' },
  'clickgui.height': { label: '窗口高度', suffix: 'px' },
  'clickgui.command-prefix': { label: '命令前缀', placeholder: '.' },
  'client-settings.language': {
    label: '语言',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: 'English' },
      { value: 1, label: '简体中文' },
    ],
  },
  'client-settings.fixed-scale': {
    label: '界面缩放倍率',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '0.5x' },
      { value: 1, label: '0.75x' },
      { value: 2, label: '1x' },
      { value: 3, label: '1.25x' },
      { value: 4, label: '1.5x' },
      { value: 5, label: '2x' },
      { value: 6, label: '2.5x' },
      { value: 7, label: '3x' },
    ],
  },
  'client-settings.webview-scale': { label: 'WebView 缩放', suffix: '%' },
  'client-settings.theme': {
    label: '主题',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '暗色' },
      { value: 1, label: '亮色' },
    ],
  },
  'client-settings.zoom-bind': { label: '缩放辅助键', type: ConfigType.KEYBIND },
  'client-settings.click-gui-key': { label: '设置界面快捷键', type: ConfigType.KEYBIND },
  'client-settings.client-command': { label: '客户端命令' },
  'client-settings.command-prefix': { label: '命令前缀', placeholder: '.' },
  'animation.no-white-heart': { label: '无白心动画' },
  'animation.old-armor': { label: '旧版盔甲动画' },
  'animation.old-backward': { label: '旧版后退动画' },
  'animation.old-blocking': { label: '旧版防砍动画' },
  'animation.animation-mode': {
    label: '格挡动画',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '1.7' },
      { value: 1, label: 'Lunar' },
      { value: 2, label: 'Swang' },
      { value: 3, label: 'Sigma' },
      { value: 4, label: 'Swank' },
      { value: 5, label: 'Swong' },
      { value: 6, label: 'Debug' },
      { value: 7, label: 'Luna' },
      { value: 8, label: 'Jigsaw' },
      { value: 9, label: 'Jello' },
      { value: 10, label: 'Push' },
    ],
  },
  'animation.old-swing': { label: '旧版挥手动画' },
  'animation.old-rod': { label: '旧版鱼竿动画' },
  'animation.no-shield': { label: '隐藏盾牌' },
  'animation.animation-sneak': { label: '潜行动画' },
  'animation.old-bow': { label: '旧版弓动画' },
  'animation.old-using': { label: '旧版使用动画' },
  'animation.block-swing': { label: '格挡挥手' },
  'animation.old-damage': { label: '旧版受伤动画' },
  'animation.old-third-person': { label: '旧版第三人称动画' },
  'more-particles.special': {
    label: '特殊粒子',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '无' },
      { value: 1, label: '心形' },
      { value: 2, label: '火焰' },
      { value: 3, label: '血液' },
    ],
  },
  'more-particles.kill-effect': {
    label: '击杀效果',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '无' },
      { value: 1, label: '闪电' },
      { value: 2, label: '爆炸' },
    ],
  },
  'motion-blur.mode': {
    label: '模式',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '经典' },
      { value: 1, label: '现代' },
    ],
  },
  'auto-gg.server-mode': {
    label: '服务器模式',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: 'Hypixel' },
      { value: 1, label: '普通' },
    ],
  },
  'armor-display.mode': {
    label: '模式',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '水平简单' },
      { value: 1, label: '垂直简单' },
      { value: 2, label: '垂直详细' },
    ],
  },
  'item-count-display.mode': {
    label: '模式',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: 'Pot PVP' },
      { value: 1, label: 'UHC' },
      { value: 2, label: '自定义' },
    ],
  },
  'keystrokes.press-anim-mode': {
    label: '按下动画',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '颜色' },
      { value: 1, label: '脉冲' },
      { value: 2, label: '波纹' },
      { value: 3, label: '绽放' },
      { value: 4, label: '堆叠' },
    ],
  },
  'keystrokes.cps-mode': {
    label: 'CPS 显示',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '下方' },
      { value: 1, label: '仅点击显示' },
      { value: 2, label: '关闭' },
    ],
  },
  'keystrokes.wasd-style': {
    label: 'WASD 样式',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '文字' },
      { value: 1, label: '三角形' },
    ],
  },
  'keystrokes.space-style': {
    label: '空格样式',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '文字' },
      { value: 1, label: '条形' },
    ],
  },
  'target-display.target-esp': {
    label: '目标 ESP',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '光环' },
      { value: 1, label: '无' },
    ],
  },
  'target-display.target-hud': {
    label: '目标 HUD',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '无' },
      { value: 1, label: '简单' },
      { value: 2, label: '精致' },
    ],
  },
  'smooth-zoom.zoom-bind': { label: '缩放快捷键', type: ConfigType.KEYBIND },
  'free-look.bind': { label: '自由视角快捷键', type: ConfigType.KEYBIND },
};

const createSyncedCategory = (id: TabId): CategoryData => ({
  id,
  title: SYNCED_TAB_TITLES[id],
  modules: [],
});

const createInitialConfigData = (): Record<string, CategoryData> => ({
  [TabId.OPTIMIZE]: createSyncedCategory(TabId.OPTIMIZE),
  [TabId.RENDER]: createSyncedCategory(TabId.RENDER),
  [TabId.TOOLS]: createSyncedCategory(TabId.TOOLS),
  [TabId.INTERFACE]: createSyncedCategory(TabId.INTERFACE),
});

const prettifyIdentity = (identity: string): string => {
  const normalized = identity
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim();

  if (!normalized) {
    return identity;
  }

  return normalized.replace(/\b\w/g, (char) => char.toUpperCase());
};

const buildConfigItemFromValue = (moduleId: string, value: RemoteModuleValueEntry): ConfigItem | null => {
  const metadata = VALUE_METADATA[`${moduleId}.${value.id}`];
  const label = metadata?.label ?? prettifyIdentity(value.id);

  if (value.type === RemoteModuleValueType.BOOLEAN) {
    return {
      id: value.id,
      label,
      type: ConfigType.CHECKBOX,
      value: value.booleanValue,
    };
  }

  if (value.type === RemoteModuleValueType.NUMBER) {
    return {
      id: value.id,
      label,
      type: metadata?.type ?? ConfigType.SLIDER,
      value: value.numberValue,
      min: value.min,
      max: value.max,
      step: value.step,
      suffix: metadata?.suffix ?? value.unit,
      options: metadata?.options,
    };
  }

  if (value.type === RemoteModuleValueType.STRING) {
    return {
      id: value.id,
      label,
      type: ConfigType.INPUT,
      value: value.stringValue ?? '',
      placeholder: metadata?.placeholder,
    };
  }

  return null;
};

const toHexChannel = (value: number): string =>
  Math.round(Math.max(0, Math.min(255, value))).toString(16).padStart(2, '0');

const hexToRgb = (hex: string): { red: number; green: number; blue: number } => {
  const normalized = hex.replace('#', '');
  return {
    red: parseInt(normalized.slice(0, 2), 16),
    green: parseInt(normalized.slice(2, 4), 16),
    blue: parseInt(normalized.slice(4, 6), 16),
  };
};

const colorGroupName = (id: string): string | null => {
  const match = id.match(/^(.*-)?(red|green|blue|alpha)$/);
  if (!match) {
    return null;
  }
  return match[1] ? match[1].slice(0, -1) : 'color';
};

const colorChannelName = (id: string): 'red' | 'green' | 'blue' | 'alpha' | null => {
  const match = id.match(/(red|green|blue|alpha)$/);
  return match ? (match[1] as 'red' | 'green' | 'blue' | 'alpha') : null;
};

const collapseColorItems = (items: ConfigItem[]): ConfigItem[] => {
  const used = new Set<string>();
  const collapsed: ConfigItem[] = [];

  items.forEach((item) => {
    if (used.has(item.id)) {
      return;
    }

    const group = colorGroupName(item.id);
    const channel = colorChannelName(item.id);
    if (item.type !== ConfigType.SLIDER || !group || channel !== 'red') {
      collapsed.push(item);
      return;
    }

    const prefix = group === 'color' ? '' : `${group}-`;
    const green = items.find((entry) => entry.id === `${prefix}green`);
    const blue = items.find((entry) => entry.id === `${prefix}blue`);
    const alpha = items.find((entry) => entry.id === `${prefix}alpha`);

    if (!green || !blue) {
      collapsed.push(item);
      return;
    }

    used.add(item.id);
    used.add(green.id);
    used.add(blue.id);
    if (alpha) {
      used.add(alpha.id);
    }

    collapsed.push({
      id: `${group}-color`,
      label: group === 'color' ? '颜色' : prettifyIdentity(group),
      type: ConfigType.COLOR,
      value: `#${toHexChannel(item.value as number)}${toHexChannel(green.value as number)}${toHexChannel(blue.value as number)}`,
      alpha: alpha?.value as number | undefined,
      channels: {
        red: item.id,
        green: green.id,
        blue: blue.id,
        alpha: alpha?.id,
      },
    });
  });

  return collapsed;
};

const buildConfigDataFromModules = (modules: RemoteModuleEntry[]): Record<string, CategoryData> => {
  const nextData = createInitialConfigData();

  modules.forEach((module) => {
    const tabId = CATEGORY_TO_TAB[module.category];
    if (!tabId) {
      return;
    }

    const metadata = MODULE_METADATA[module.id];
    nextData[tabId].modules.push({
      id: module.id,
      title: metadata?.title ?? prettifyIdentity(module.id),
      description: metadata?.description ?? module.category,
      icon: metadata?.icon ?? Box,
      enabled: module.enabled,
      children: collapseColorItems(
        module.values
          .map((value) => buildConfigItemFromValue(module.id, value))
          .filter((value): value is ConfigItem => value !== null),
      ),
    });
  });

  return nextData;
};

const resolveValueType = (value: ConfigValue): RemoteModuleValueType => {
  if (typeof value === 'boolean') {
    return RemoteModuleValueType.BOOLEAN;
  }

  if (typeof value === 'number') {
    return RemoteModuleValueType.NUMBER;
  }

  return RemoteModuleValueType.STRING;
};

const HARDWARE_CONFIRMATION_KEY = 'fpsmaster.hardwareAccelerationConfirmationDeadline';
const HARDWARE_CONFIRMATION_MS = 5000;
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

export const SettingsPanel: React.FC<SettingsPanelProps> = ({ activeTab, immersiveMode, setImmersiveMode, wsStatus }) => {
  const [configData, setConfigData] = useState<Record<string, CategoryData>>(() => createInitialConfigData());
  const [syncReady, setSyncReady] = useState(false);
  const [profilesStatus, setProfilesStatus] = useState<ConfigProfilesPacket | null>(null);
  const [clientConfig, setClientConfig] = useState<ClientConfigPacket | null>(null);
  const [profileName, setProfileName] = useState('');
  const [renameTarget, setRenameTarget] = useState('');
  const [profilePath, setProfilePath] = useState('');
  const [profileBusy, setProfileBusy] = useState(false);
  const [hardwareConfirmationDeadline, setHardwareConfirmationDeadline] = useState<number | null>(() => {
    const storedDeadline = Number(localStorage.getItem(HARDWARE_CONFIRMATION_KEY));
    return Number.isFinite(storedDeadline) && storedDeadline > 0 ? storedDeadline : null;
  });
  const [hardwareConfirmationRemaining, setHardwareConfirmationRemaining] = useState(0);

  useEffect(() => {
    const handleModuleList = (packet: ModuleListPacket) => {
      setConfigData(buildConfigDataFromModules(packet.modules));
      setSyncReady(true);
    };

    PacketProcessor.register(12, handleModuleList);
    return () => {
      PacketProcessor.unregister(12, handleModuleList);
    };
  }, []);

  useEffect(() => {
    const handleProfiles = (packet: ConfigProfilesPacket) => {
      setProfilesStatus(packet);
      setProfileBusy(false);
    };

    PacketProcessor.register(23, handleProfiles);
    return () => {
      PacketProcessor.unregister(23, handleProfiles);
    };
  }, []);

  useEffect(() => {
    const handleClientConfig = (packet: ClientConfigPacket) => {
      setClientConfig(packet);
    };

    PacketProcessor.register(16, handleClientConfig);
    return () => {
      PacketProcessor.unregister(16, handleClientConfig);
    };
  }, []);

  useEffect(() => {
    if (wsStatus !== 'open') {
      return;
    }

    NetworkManager.send(new ModuleListRequestPacket());
    NetworkManager.send(new ConfigProfilesRequestPacket());
    NetworkManager.send(new ClientConfigRequestPacket());
  }, [wsStatus]);

  const refreshProfiles = () => {
    if (wsStatus !== 'open') {
      return;
    }

    setProfileBusy(true);
    NetworkManager.send(new ConfigProfilesRequestPacket());
  };

  const sendProfileAction = (action: string, name = '', targetName = '') => {
    if (wsStatus !== 'open' || profileBusy) {
      return;
    }

    setProfileBusy(true);
    NetworkManager.send(new ConfigProfileActionPacket(action, name, targetName));
  };

  const updateAnonymousDataEnabled = (value: boolean) => {
    if (wsStatus !== 'open') {
      return;
    }

    setClientConfig((prev) => {
      if (!prev) {
        return prev;
      }
      const next = new ClientConfigPacket();
      next.musicVolume = prev.musicVolume;
      next.anonymousDataEnabled = value;
      next.telemetryInstanceId = prev.telemetryInstanceId;
      return next;
    });

    NetworkManager.send(new ClientConfigUpdatePacket(clientConfig?.musicVolume ?? 75, {
      updateMusicVolume: false,
      anonymousDataEnabled: value,
    }));
  };

  const updateClientPreference = (changes: Partial<ClientConfigPacket>) => {
    if (wsStatus !== 'open' || !clientConfig) {
      return;
    }

    const next = cloneClientConfig(clientConfig);
    Object.assign(next, changes);
    setClientConfig(next);
    NetworkManager.send(new ClientConfigUpdatePacket(next.musicVolume, {
      updateMusicVolume: false,
      clientPreferences: next,
    }));
  };

  const toggleModule = (tabId: TabId, moduleId: string, enabled: boolean) => {
    if (wsStatus !== 'open') {
      return;
    }

    setConfigData((prev) => ({
      ...prev,
      [tabId]: {
        ...prev[tabId],
        modules: prev[tabId].modules.map((module) =>
          module.id === moduleId ? { ...module, enabled } : module,
        ),
      },
    }));

    NetworkManager.send(new ModuleTogglePacket(moduleId, enabled));
  };

  const updateSetting = (tabId: TabId, moduleId: string, settingId: string, value: ConfigValue) => {
    if (wsStatus !== 'open') {
      return;
    }

    setConfigData((prev) => ({
      ...prev,
      [tabId]: {
        ...prev[tabId],
        modules: prev[tabId].modules.map((module) => {
          if (module.id !== moduleId) {
            return module;
          }

          return {
            ...module,
            children: module.children.map((child) =>
              child.id === settingId ? { ...child, value } : child,
            ),
          };
        }),
      },
    }));

    NetworkManager.send(
      new ModuleValueUpdatePacket(moduleId, settingId, resolveValueType(value), value),
    );
  };

  const updateColorSetting = (tabId: TabId, moduleId: string, item: ConfigItem, hex: string, alpha?: number) => {
    if (wsStatus !== 'open' || !item.channels) {
      return;
    }

    const rgb = hexToRgb(hex);
    const updates = [
      { id: item.channels.red, value: rgb.red },
      { id: item.channels.green, value: rgb.green },
      { id: item.channels.blue, value: rgb.blue },
    ];
    if (item.channels.alpha && alpha !== undefined) {
      updates.push({ id: item.channels.alpha, value: alpha });
    }

    setConfigData((prev) => ({
      ...prev,
      [tabId]: {
        ...prev[tabId],
        modules: prev[tabId].modules.map((module) => {
          if (module.id !== moduleId) {
            return module;
          }

          return {
            ...module,
            children: module.children.map((child) =>
              child.id === item.id ? { ...child, value: hex, alpha } : child,
            ),
          };
        }),
      },
    }));

    updates.forEach((update) => {
      NetworkManager.send(
        new ModuleValueUpdatePacket(moduleId, update.id, RemoteModuleValueType.NUMBER, update.value),
      );
    });
  };

  const renderConfigItem = (module: FeatureModule, item: ConfigItem) => {
    switch (item.type) {
      case ConfigType.CHECKBOX:
        return (
          <div key={item.id} className="col-span-1">
            <Checkbox
              label={item.label}
              checked={item.value as boolean}
              onChange={(value) => updateSetting(activeTab, module.id, item.id, value)}
            />
          </div>
        );

      case ConfigType.SLIDER:
        return (
          <div key={item.id} className="col-span-2">
            <Slider
              label={item.label}
              value={item.value as number}
              min={item.min || 0}
              max={item.max || 100}
              step={item.step || 1}
              suffix={item.suffix}
              onChange={(value) => updateSetting(activeTab, module.id, item.id, value)}
            />
          </div>
        );

      case ConfigType.SELECT:
        return (
          <div key={item.id} className="col-span-2">
            <SelectBox
              label={item.label}
              value={item.value as number}
              options={item.options || []}
              onChange={(value) => updateSetting(activeTab, module.id, item.id, Number(value))}
            />
          </div>
        );

      case ConfigType.KEYBIND:
        return (
          <div key={item.id} className="col-span-2">
            <KeybindInput
              label={item.label}
              value={item.value as number}
              onChange={(value) => updateSetting(activeTab, module.id, item.id, value)}
            />
          </div>
        );

      case ConfigType.COLOR:
        return (
          <div key={item.id} className="col-span-2">
            <ColorPicker
              label={item.label}
              value={item.value as string}
              alpha={item.alpha}
              onChange={(hex, alpha) => updateColorSetting(activeTab, module.id, item, hex, alpha)}
            />
          </div>
        );

      case ConfigType.INPUT:
        return (
          <div key={item.id} className="col-span-2 flex flex-col gap-1.5 py-1">
            <span className="text-xs text-neutral-400 font-medium">{item.label}</span>
            <input
              type="text"
              value={item.value as string}
              placeholder={item.placeholder}
              onChange={(event) => updateSetting(activeTab, module.id, item.id, event.target.value)}
              className="bg-neutral-800/50 border border-white/5 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:border-indigo-500/50 transition-colors w-full"
            />
          </div>
        );

      default:
        return null;
    }
  };

  const renderModule = (module: FeatureModule) => {
    const hasSettings = module.children.length > 0;

    return (
      <FeatureCard
        key={module.id}
        title={module.title}
        description={module.description || ''}
        icon={module.icon || Box}
        enabled={module.enabled}
        onToggle={(value) => toggleModule(activeTab, module.id, value)}
      >
        {hasSettings ? (
          <div className="grid grid-cols-2 gap-x-4 gap-y-2">
            {module.children.map((child) => renderConfigItem(module, child))}
          </div>
        ) : null}
      </FeatureCard>
    );
  };

  const updateHardwareAcceleration = (value: boolean) => {
    if (value) {
      const deadline = Date.now() + HARDWARE_CONFIRMATION_MS;
      localStorage.setItem(HARDWARE_CONFIRMATION_KEY, String(deadline));
      setHardwareConfirmationDeadline(deadline);
      setHardwareConfirmationRemaining(HARDWARE_CONFIRMATION_MS);
    } else {
      localStorage.removeItem(HARDWARE_CONFIRMATION_KEY);
      setHardwareConfirmationDeadline(null);
      setHardwareConfirmationRemaining(0);
    }

    updateSetting(TabId.INTERFACE, 'clickgui', 'hardware-acceleration', value);
  };

  const confirmHardwareAcceleration = () => {
    localStorage.removeItem(HARDWARE_CONFIRMATION_KEY);
    setHardwareConfirmationDeadline(null);
    setHardwareConfirmationRemaining(0);
  };

  const clickGuiModule = configData[TabId.INTERFACE].modules.find((module) => module.id === 'clickgui');
  const developerMetrics = clickGuiModule?.children.find((item) => item.id === 'developer-metrics');
  const hardwareAcceleration = clickGuiModule?.children.find((item) => item.id === 'hardware-acceleration');
  const developerMetricsEnabled = developerMetrics?.value === true;
  const hardwareAccelerationEnabled = hardwareAcceleration?.value === true;
  const hardwareConfirmationActive = hardwareAccelerationEnabled && hardwareConfirmationDeadline !== null;

  useEffect(() => {
    if (!hardwareConfirmationDeadline || !hardwareAccelerationEnabled) {
      return undefined;
    }

    const interval = window.setInterval(() => {
      const remaining = hardwareConfirmationDeadline - Date.now();
      if (remaining <= 0) {
        setHardwareConfirmationRemaining(0);

        if (wsStatus !== 'open') {
          return;
        }

        window.clearInterval(interval);
        localStorage.removeItem(HARDWARE_CONFIRMATION_KEY);
        setHardwareConfirmationDeadline(null);
        updateSetting(TabId.INTERFACE, 'clickgui', 'hardware-acceleration', false);
        return;
      }

      setHardwareConfirmationRemaining(remaining);
    }, 100);

    return () => window.clearInterval(interval);
  }, [hardwareConfirmationDeadline, hardwareAccelerationEnabled, wsStatus]);

  if (activeTab === TabId.MUSIC) {
    return (
      <motion.div
        key="music"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="h-full w-full"
      >
        <MusicPlayer immersiveMode={immersiveMode} setImmersiveMode={setImmersiveMode} />
      </motion.div>
    );
  }

  const currentCategory = configData[activeTab];
  const isGeneric = !!currentCategory;

  const renderContent = () => {
    if (isGeneric) {
      if (currentCategory.modules.length === 0) {
        return (
          <div className="flex flex-col items-center justify-center h-full text-neutral-500 gap-4 mt-20">
            <div className="p-4 rounded-full bg-neutral-800/50 border border-white/5 ring-1 ring-white/10">
              <Sparkles size={32} className="text-neutral-600" />
            </div>
            <p className="text-sm font-medium">
              {syncReady ? '当前分类暂无模块' : '正在同步模块列表...'}
            </p>
          </div>
        );
      }

      return <div className="flex flex-col gap-4">{currentCategory.modules.map(renderModule)}</div>;
    }

    if (activeTab === TabId.SETTINGS) {
      const activeProfile = profilesStatus?.activeProfile || '';
      const profiles = profilesStatus?.profiles || [];
      const canUseProfileName = wsStatus === 'open' && !profileBusy && profileName.trim().length > 0;
      const canRenameProfile = canUseProfileName && renameTarget.trim().length > 0;
      const canUseProfilePath = wsStatus === 'open' && !profileBusy && profilePath.trim().length > 0;
      const anonymousDataEnabled = clientConfig?.anonymousDataEnabled === true;

      return (
        <div className="flex flex-col gap-6">
          <div className="bg-neutral-900/40 rounded-xl p-5 border border-white/5">
            <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
              <Activity size={16} className="text-indigo-400" />
              客户端选项
            </h3>
            <div className="space-y-3">
              <div className="flex justify-between items-center">
                <div className="min-w-0">
                  <span className="block text-xs text-neutral-400">匿名数据上报</span>
                  <span className="block truncate text-[10px] text-neutral-600">
                    {clientConfig?.telemetryInstanceId || '等待同步'}
                  </span>
                </div>
                <Toggle
                  checked={anonymousDataEnabled}
                  onChange={updateAnonymousDataEnabled}
                />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">已完成首次引导</span>
                <Toggle
                  checked={clientConfig?.oobeCompleted === true}
                  onChange={(value) => updateClientPreference({ oobeCompleted: value })}
                />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">反作弊偏好</span>
                <Toggle
                  checked={clientConfig?.antiCheatEnabled !== false}
                  onChange={(value) => updateClientPreference({ antiCheatEnabled: value })}
                />
              </div>
              <div className="flex flex-col gap-1.5 py-1">
                <span className="text-xs text-neutral-400">主菜单背景</span>
                <select
                  value={clientConfig?.background || 'panorama_1'}
                  onChange={(event) => updateClientPreference({ background: event.target.value })}
                  disabled={wsStatus !== 'open' || !clientConfig}
                  className="bg-neutral-800/50 border border-white/5 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-indigo-500/50 transition-colors w-full disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {BACKGROUND_OPTIONS.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">WebView 性能指标</span>
                <Toggle
                  checked={developerMetricsEnabled}
                  onChange={(value) => updateSetting(TabId.INTERFACE, 'clickgui', 'developer-metrics', value)}
                />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">WebView 硬件加速</span>
                <Toggle
                  checked={hardwareAccelerationEnabled}
                  onChange={updateHardwareAcceleration}
                />
              </div>
              {hardwareConfirmationActive ? (
                <div className="flex items-center justify-between gap-3 rounded-lg border border-amber-500/20 bg-amber-500/10 px-3 py-2">
                  <div className="flex items-center gap-2 text-xs text-amber-100">
                    <AlertTriangle size={14} className="text-amber-300" />
                    <span>确认画面正常，{Math.ceil(hardwareConfirmationRemaining / 1000)}s 后自动回退</span>
                  </div>
                  <button
                    type="button"
                    onClick={confirmHardwareAcceleration}
                    className="shrink-0 rounded-md border border-amber-300/20 bg-amber-300/15 px-2.5 py-1 text-xs font-medium text-amber-50 transition-colors hover:bg-amber-300/25"
                  >
                    确认
                  </button>
                </div>
              ) : null}
            </div>
          </div>

          <div className="bg-neutral-900/40 rounded-xl p-5 border border-white/5">
            <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
              <Database size={16} className="text-indigo-400" />
              配置档案
            </h3>
            <div className="space-y-4">
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <div className="truncate text-sm font-semibold text-white">
                    当前: {activeProfile || '未知'}
                  </div>
                  <div className="text-xs text-neutral-500">
                    {profiles.length > 0 ? `${profiles.length} 个配置档案` : '正在同步配置档案'}
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => sendProfileAction('save')}
                    disabled={profileBusy || wsStatus !== 'open'}
                    className="rounded-lg border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-neutral-200 transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    保存当前
                  </button>
                  <button
                    type="button"
                    onClick={refreshProfiles}
                    disabled={profileBusy || wsStatus !== 'open'}
                    className="rounded-lg border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-neutral-200 transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    刷新
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-2">
                {profiles.map((profile) => {
                  const current = profile === activeProfile;
                  return (
                    <div
                      key={profile}
                      className={`flex items-center justify-between gap-3 rounded-lg border px-3 py-2 ${
                        current ? 'border-indigo-400/30 bg-indigo-400/10' : 'border-white/5 bg-black/20'
                      }`}
                    >
                      <span className="truncate text-xs font-medium text-white">
                        {profile}{current ? ' · 当前' : ''}
                      </span>
                      <div className="flex shrink-0 gap-2">
                        <button
                          type="button"
                          onClick={() => sendProfileAction('load', profile)}
                          disabled={current || profileBusy || wsStatus !== 'open'}
                          className="rounded-md bg-white/5 px-2 py-1 text-[11px] text-neutral-200 transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
                        >
                          加载
                        </button>
                        <button
                          type="button"
                          onClick={() => sendProfileAction('delete', profile)}
                          disabled={current || profile === 'default' || profileBusy || wsStatus !== 'open'}
                          className="rounded-md bg-red-400/10 px-2 py-1 text-[11px] text-red-100 transition-colors hover:bg-red-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                        >
                          删除
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>

              <div className="grid grid-cols-1 gap-3">
                <input
                  type="text"
                  value={profileName}
                  placeholder="配置名"
                  onChange={(event) => setProfileName(event.target.value)}
                  className="bg-neutral-800/50 border border-white/5 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-indigo-500/50 transition-colors w-full"
                />
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => sendProfileAction('create', profileName.trim())}
                    disabled={!canUseProfileName}
                    className="rounded-lg bg-white/5 px-3 py-2 text-xs font-semibold text-white transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:text-neutral-500"
                  >
                    创建
                  </button>
                  <button
                    type="button"
                    onClick={() => sendProfileAction('save', profileName.trim())}
                    disabled={!canUseProfileName}
                    className="rounded-lg bg-white/5 px-3 py-2 text-xs font-semibold text-white transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:text-neutral-500"
                  >
                    另存为
                  </button>
                </div>
                <input
                  type="text"
                  value={renameTarget}
                  placeholder="重命名为"
                  onChange={(event) => setRenameTarget(event.target.value)}
                  className="bg-neutral-800/50 border border-white/5 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-indigo-500/50 transition-colors w-full"
                />
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => sendProfileAction('rename', profileName.trim(), renameTarget.trim())}
                    disabled={!canRenameProfile}
                    className="rounded-lg bg-white/5 px-3 py-2 text-xs font-semibold text-white transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:text-neutral-500"
                  >
                    重命名
                  </button>
                  <button
                    type="button"
                    onClick={() => sendProfileAction('alloff')}
                    disabled={profileBusy || wsStatus !== 'open'}
                    className="rounded-lg border border-amber-400/20 bg-amber-400/10 px-3 py-2 text-xs font-semibold text-amber-100 transition-colors hover:bg-amber-400/20 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    全部关闭
                  </button>
                </div>
                <input
                  type="text"
                  value={profilePath}
                  placeholder="导入/导出路径"
                  onChange={(event) => setProfilePath(event.target.value)}
                  className="bg-neutral-800/50 border border-white/5 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-indigo-500/50 transition-colors w-full"
                />
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => sendProfileAction('import', profilePath.trim())}
                    disabled={!canUseProfilePath}
                    className="rounded-lg bg-white/5 px-3 py-2 text-xs font-semibold text-white transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:text-neutral-500"
                  >
                    导入
                  </button>
                  <button
                    type="button"
                    onClick={() => sendProfileAction('export', profilePath.trim())}
                    disabled={!canUseProfilePath}
                    className="rounded-lg bg-white/5 px-3 py-2 text-xs font-semibold text-white transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:text-neutral-500"
                  >
                    导出当前
                  </button>
                </div>
              </div>

              {profilesStatus?.message && profilesStatus.message !== 'OK' ? (
                <div className={`rounded-lg border px-3 py-2 text-xs ${
                  profilesStatus.success
                    ? 'border-emerald-400/20 bg-emerald-400/10 text-emerald-100'
                    : 'border-red-400/20 bg-red-400/10 text-red-100'
                }`}>
                  {profilesStatus.message}
                </div>
              ) : null}
            </div>
          </div>
        </div>
      );
    }

    return (
      <div className="flex flex-col items-center justify-center h-full text-neutral-500 gap-4 mt-20">
        <div className="p-4 rounded-full bg-neutral-800/50 border border-white/5 ring-1 ring-white/10">
          <Sparkles size={32} className="text-neutral-600" />
        </div>
        <p className="text-sm font-medium">该模块正在开发中...</p>
      </div>
    );
  };

  return (
    <div className="flex flex-col h-full w-full relative z-10">
      <div className="flex items-center justify-between px-8 pt-8 pb-4 shrink-0">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold text-white tracking-tight flex items-center gap-3">
            {isGeneric ? currentCategory.title : activeTab === TabId.SETTINGS ? '全局设置' : ''}
          </h1>
          <p className="text-[10px] text-neutral-500 font-bold tracking-widest uppercase opacity-70">
            FPSMaster Configuration
          </p>
        </div>

      </div>

      <div className="flex-1 overflow-hidden relative">
        <div className="absolute inset-0 overflow-y-auto px-8 pb-8 scrollbar-hide">
          <AnimatePresence mode="wait">
            <motion.div
              key={activeTab}
              initial={{ opacity: 0, x: 10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -10 }}
              transition={{ duration: 0.2 }}
            >
              {renderContent()}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
};
