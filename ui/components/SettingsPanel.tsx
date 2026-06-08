import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Sparkles, Shield, Database, Box, Zap, Eye, Activity, Clock3, Layout, AlertTriangle } from 'lucide-react';
import { TabId, ConfigType, FeatureModule, ConfigItem, CategoryData, ConfigValue } from '../types';
import { Toggle, Checkbox, Slider, FeatureCard } from './Controls';
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
  'clickgui.scale': { label: '界面缩放', suffix: '%' },
  'clickgui.width': { label: '窗口宽度', suffix: 'px' },
  'clickgui.height': { label: '窗口高度', suffix: 'px' },
  'clickgui.command-prefix': { label: '命令前缀', placeholder: '.' },
  'animation.no-white-heart': { label: '无白心动画' },
  'animation.old-armor': { label: '旧版盔甲动画' },
  'animation.old-backward': { label: '旧版后退动画' },
  'animation.old-blocking': { label: '旧版防砍动画' },
  'animation.old-swing': { label: '旧版挥手动画' },
  'animation.old-rod': { label: '旧版鱼竿动画' },
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
      type: ConfigType.SLIDER,
      value: value.numberValue,
      min: value.min,
      max: value.max,
      step: value.step,
      suffix: metadata?.suffix ?? value.unit,
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
      children: module.values
        .map((value) => buildConfigItemFromValue(module.id, value))
        .filter((value): value is ConfigItem => value !== null),
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

export const SettingsPanel: React.FC<SettingsPanelProps> = ({ activeTab, immersiveMode, setImmersiveMode, wsStatus }) => {
  const [configData, setConfigData] = useState<Record<string, CategoryData>>(() => createInitialConfigData());
  const [syncReady, setSyncReady] = useState(false);
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
    if (wsStatus !== 'open') {
      return;
    }

    NetworkManager.send(new ModuleListRequestPacket());
  }, [wsStatus]);

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
      return (
        <div className="flex flex-col gap-6">
          <div className="bg-neutral-900/40 rounded-xl p-5 border border-white/5">
            <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
              <Shield size={16} className="text-indigo-400" />
              账号与安全
            </h3>
            <div className="space-y-3">
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">自动登录</span>
                <Toggle checked={true} onChange={() => {}} />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-neutral-400">云端配置同步</span>
                <Toggle checked={false} onChange={() => {}} />
              </div>
            </div>
          </div>

          <div className="bg-neutral-900/40 rounded-xl p-5 border border-white/5">
            <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
              <Activity size={16} className="text-indigo-400" />
              开发选项
            </h3>
            <div className="space-y-3">
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
              缓存管理
            </h3>
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <span className="text-sm text-white">清理缓存</span>
                <span className="text-xs text-neutral-500">释放约 240MB 空间</span>
              </div>
              <button className="px-3 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-xs text-white border border-white/10 transition-colors">
                立即清理
              </button>
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
