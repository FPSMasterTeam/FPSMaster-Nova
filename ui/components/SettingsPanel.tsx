import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Sparkles, Shield, Database, Box, Zap, Eye, Activity, Clock3, Layout, AlertTriangle } from 'lucide-react';
import { TabId, ConfigType, FeatureModule, ConfigItem, CategoryData, ConfigValue } from '../types';
import { useT } from '../i18n';
import { Toggle, Checkbox, Slider, FeatureCard, SelectBox, CustomSelect, KeybindInput, ColorPicker } from './Controls';
import { MusicPlayer } from './MusicPlayer';
import { NetworkManager } from '../network/WebSocketClient';
import { PacketProcessor } from '../network/PacketProcessor';
import { UIEventPacket } from '../network/packets/UIEventPacket';
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

// Values are i18n keys, resolved with t() at render time.
const SYNCED_TAB_TITLES: Record<TabId, string> = {
  [TabId.OPTIMIZE]: 'tab.optimize',
  [TabId.RENDER]: 'tab.render',
  [TabId.TOOLS]: 'tab.tools',
  [TabId.INTERFACE]: 'tab.interface',
  [TabId.MUSIC]: 'tab.music',
  [TabId.SETTINGS]: 'tab.settings',
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
  'mini-map.shape': {
    label: '形状',
    type: ConfigType.SELECT,
    options: [
      { value: 0, label: '方形' },
      { value: 1, label: '圆形' },
    ],
  },
  'mini-map.show-players': { label: '显示玩家' },
  'mini-map.radius': { label: '范围', suffix: ' 格' },
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

// --- i18n -------------------------------------------------------------------
// Module names / descriptions / setting labels arrive already-translated from
// the client (sourced from the .lang files). The UI only needs to localise the
// bits that stay structural here: collapsed colour-group labels and SELECT
// option labels. Locale follows the client-settings.language value.
type Locale = 'en' | 'zh';

const localeFromModules = (modules: RemoteModuleEntry[]): Locale => {
  const settings = modules.find((module) => module.id === 'client-settings');
  const language = settings?.values.find((value) => value.id === 'language')?.numberValue ?? 1;
  return language === 0 ? 'en' : 'zh';
};

const COLOR_GROUP_LABELS: Record<string, { en: string; zh: string }> = {
  color: { en: 'Color', zh: '颜色' },
  background: { en: 'Background', zh: '背景' },
  outline: { en: 'Outline', zh: '描边' },
  border: { en: 'Border', zh: '边框' },
  panel: { en: 'Panel', zh: '面板' },
  accent: { en: 'Accent', zh: '强调色' },
  esp: { en: 'ESP', zh: 'ESP' },
  fill: { en: 'Fill', zh: '填充' },
  font: { en: 'Font', zh: '字体' },
  text: { en: 'Text', zh: '文字' },
  friend: { en: 'Friend', zh: '好友' },
  enemy: { en: 'Enemy', zh: '敌人' },
  pressed: { en: 'Pressed', zh: '按下' },
  'pressed-font': { en: 'Pressed Font', zh: '按下字体' },
  'press-anim': { en: 'Press Animation', zh: '按下动画' },
};

const colorGroupLabel = (group: string, locale: Locale): string =>
  COLOR_GROUP_LABELS[group]?.[locale] ?? prettifyIdentity(group);

// English text for the Chinese SELECT option labels declared above. Proper nouns
// (Lunar, Hypixel, 1.7, scale factors, …) are intentionally absent and pass through.
const OPTION_LABELS_EN: Record<string, string> = {
  普通: 'Normal',
  自定义: 'Custom',
  水平简单: 'Horizontal Simple',
  垂直简单: 'Vertical Simple',
  垂直详细: 'Vertical Detailed',
  颜色: 'Color',
  脉冲: 'Pulse',
  波纹: 'Ripple',
  绽放: 'Bloom',
  堆叠: 'Stack',
  下方: 'Below',
  仅点击显示: 'On Click',
  关闭: 'Off',
  文字: 'Text',
  三角形: 'Triangle',
  条形: 'Bar',
  光环: 'Aura',
  无: 'None',
  简单: 'Simple',
  精致: 'Detailed',
  心形: 'Heart',
  火焰: 'Flame',
  血液: 'Blood',
  闪电: 'Lightning',
  爆炸: 'Explosion',
  经典: 'Classic',
  现代: 'Modern',
  方形: 'Square',
  圆形: 'Circle',
  暗色: 'Dark',
  亮色: 'Light',
};

const localizeOption = (label: string, locale: Locale): string =>
  locale === 'en' ? OPTION_LABELS_EN[label] ?? label : label;

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

const buildConfigItemFromValue = (
  moduleId: string,
  value: RemoteModuleValueEntry,
  locale: Locale,
): ConfigItem | null => {
  const metadata = VALUE_METADATA[`${moduleId}.${value.id}`];
  // Prefer the translated label sent by the client; fall back to local metadata.
  const label = value.label || metadata?.label || prettifyIdentity(value.id);

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
      options: metadata?.options?.map((option) => ({
        ...option,
        label: localizeOption(option.label, locale),
      })),
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

const collapseColorItems = (items: ConfigItem[], locale: Locale): ConfigItem[] => {
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
      label: colorGroupLabel(group, locale),
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
  const locale = localeFromModules(modules);

  modules.forEach((module) => {
    const tabId = CATEGORY_TO_TAB[module.category];
    if (!tabId) {
      return;
    }

    const metadata = MODULE_METADATA[module.id];
    nextData[tabId].modules.push({
      id: module.id,
      // Prefer the translated text sent by the client; fall back to local metadata.
      title: module.displayName || metadata?.title || prettifyIdentity(module.id),
      description: module.description || metadata?.description || module.category,
      icon: metadata?.icon ?? Box,
      enabled: module.enabled,
      children: collapseColorItems(
        module.values
          .map((value) => buildConfigItemFromValue(module.id, value, locale))
          .filter((value): value is ConfigItem => value !== null),
        locale,
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
  const t = useT();
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
              {syncReady ? t('settings.noModules') : t('settings.syncingModules')}
            </p>
          </div>
        );
      }

      // ClickGUI and the (removed) hud-editor are not toggleable features; ClickGUI's settings live
      // in the Settings tab instead.
      const features = currentCategory.modules.filter(
        (module) => module.id !== 'clickgui' && module.id !== 'hud-editor'
      );
      return <div className="flex flex-col gap-4">{features.map(renderModule)}</div>;
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
              {t('settings.clientOptions')}
            </h3>
            <div className="space-y-3">
              <div className="flex justify-between items-center">
                <div className="min-w-0">
                  <span className="block text-xs text-neutral-400">{t('settings.anonymousReport')}</span>
                  <span className="block truncate text-[10px] text-neutral-600">
                    {clientConfig?.telemetryInstanceId || t('settings.waitingSync')}
                  </span>
                </div>
                <Toggle
                  checked={anonymousDataEnabled}
                  onChange={updateAnonymousDataEnabled}
                />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">{t('settings.oobeCompleted')}</span>
                <Toggle
                  checked={clientConfig?.oobeCompleted === true}
                  onChange={(value) => updateClientPreference({ oobeCompleted: value })}
                />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">{t('settings.antiCheatPref')}</span>
                <Toggle
                  checked={clientConfig?.antiCheatEnabled !== false}
                  onChange={(value) => updateClientPreference({ antiCheatEnabled: value })}
                />
              </div>
              <div className="flex flex-col gap-1.5 py-1">
                <span className="text-xs text-neutral-400">{t('settings.menuBackground')}</span>
                <CustomSelect
                  value={clientConfig?.background || 'panorama_1'}
                  options={BACKGROUND_OPTIONS.map((option) => ({ value: option.id, label: t(`bg.${option.id}`) }))}
                  onChange={(value) => updateClientPreference({ background: String(value) })}
                  disabled={wsStatus !== 'open' || !clientConfig}
                />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">{t('settings.webviewMetrics')}</span>
                <Toggle
                  checked={developerMetricsEnabled}
                  onChange={(value) => updateSetting(TabId.INTERFACE, 'clickgui', 'developer-metrics', value)}
                />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">{t('settings.webviewHwAccel')}</span>
                <Toggle
                  checked={hardwareAccelerationEnabled}
                  onChange={updateHardwareAcceleration}
                />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">{t('settings.editHud')}</span>
                <button
                  type="button"
                  onClick={() => NetworkManager.send(new UIEventPacket('open-hud-editor'))}
                  disabled={wsStatus !== 'open'}
                  className="shrink-0 rounded-md border border-indigo-300/20 bg-indigo-300/15 px-2.5 py-1 text-xs font-medium text-indigo-50 transition-colors hover:bg-indigo-300/25 disabled:opacity-50"
                >
                  {t('settings.editHudButton')}
                </button>
              </div>
              {hardwareConfirmationActive ? (
                <div className="flex items-center justify-between gap-3 rounded-lg border border-amber-500/20 bg-amber-500/10 px-3 py-2">
                  <div className="flex items-center gap-2 text-xs text-amber-100">
                    <AlertTriangle size={14} className="text-amber-300" />
                    <span>{t('settings.hwConfirm', { seconds: Math.ceil(hardwareConfirmationRemaining / 1000) })}</span>
                  </div>
                  <button
                    type="button"
                    onClick={confirmHardwareAcceleration}
                    className="shrink-0 rounded-md border border-amber-300/20 bg-amber-300/15 px-2.5 py-1 text-xs font-medium text-amber-50 transition-colors hover:bg-amber-300/25"
                  >
                    {t('common.confirm')}
                  </button>
                </div>
              ) : null}
            </div>
          </div>

          {clickGuiModule ? renderModule(clickGuiModule) : null}

          <div className="bg-neutral-900/40 rounded-xl p-5 border border-white/5">
            <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
              <Database size={16} className="text-indigo-400" />
              {t('settings.configProfiles')}
            </h3>
            <div className="space-y-4">
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <div className="truncate text-sm font-semibold text-white">
                    {t('settings.currentProfile', { name: activeProfile || t('settings.unknown') })}
                  </div>
                  <div className="text-xs text-neutral-500">
                    {profiles.length > 0 ? t('settings.profileCount', { count: profiles.length }) : t('settings.syncingProfiles')}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => sendProfileAction('save')}
                  disabled={profileBusy || wsStatus !== 'open'}
                  className="shrink-0 rounded-lg border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-neutral-200 transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {t('settings.saveCurrent')}
                </button>
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
                      <button
                        type="button"
                        onClick={() => sendProfileAction('load', profile)}
                        disabled={current || profileBusy || wsStatus !== 'open'}
                        title={current ? '' : t('common.load')}
                        className="min-w-0 flex-1 truncate text-left text-xs font-medium text-white transition-colors hover:text-indigo-200 disabled:cursor-default disabled:hover:text-white"
                      >
                        {profile}{current ? t('settings.currentSuffix') : ''}
                      </button>
                      <button
                        type="button"
                        onClick={() => sendProfileAction('delete', profile)}
                        disabled={current || profile === 'default' || profileBusy || wsStatus !== 'open'}
                        title={t('common.delete')}
                        className="shrink-0 rounded-md px-2 py-1 text-[11px] text-neutral-400 transition-colors hover:bg-red-400/15 hover:text-red-200 disabled:cursor-not-allowed disabled:opacity-30"
                      >
                        ✕
                      </button>
                    </div>
                  );
                })}
              </div>

              <div className="flex gap-2">
                <input
                  type="text"
                  value={profileName}
                  placeholder={t('settings.profileNamePlaceholder')}
                  onChange={(event) => setProfileName(event.target.value)}
                  className="min-w-0 flex-1 bg-neutral-800/50 border border-white/5 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-indigo-500/50 transition-colors"
                />
                <button
                  type="button"
                  onClick={() => sendProfileAction('create', profileName.trim())}
                  disabled={!canUseProfileName}
                  className="shrink-0 rounded-lg bg-white/5 px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:text-neutral-500"
                >
                  {t('settings.create')}
                </button>
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
        <p className="text-sm font-medium">{t('settings.moduleInDev')}</p>
      </div>
    );
  };

  return (
    <div className="flex flex-col h-full w-full relative z-10">
      <div className="flex items-center justify-between px-8 pt-8 pb-4 shrink-0">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold text-white tracking-tight flex items-center gap-3">
            {isGeneric ? t(currentCategory.title) : activeTab === TabId.SETTINGS ? t('settings.globalSettings') : ''}
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
