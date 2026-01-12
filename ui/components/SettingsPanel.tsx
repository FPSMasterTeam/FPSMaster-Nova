import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Sparkles, Bot, Shield, Database, Box } from 'lucide-react';
import { TabId, ConfigType, FeatureModule, ConfigItem } from '../types';
import { Toggle, Checkbox, Slider, FeatureCard, ModeSelect } from './Controls';
import { MusicPlayer } from './MusicPlayer';
import { APP_DATA } from '../data';

interface SettingsPanelProps {
  activeTab: TabId;
  setImmersiveMode: (v: boolean) => void;
}

export const SettingsPanel: React.FC<SettingsPanelProps> = ({ activeTab, setImmersiveMode }) => {
  // Local state to manage the configuration values
  // In a real app, this would be a Context or Redux store
  const [configData, setConfigData] = useState(APP_DATA);

  // Handlers for updating state
  const toggleModule = (tabId: TabId, moduleId: string, val: boolean) => {
    setConfigData(prev => ({
        ...prev,
        [tabId]: {
            ...prev[tabId],
            modules: prev[tabId].modules.map(m => m.id === moduleId ? { ...m, enabled: val } : m)
        }
    }));
  };

  const updateSetting = (tabId: TabId, moduleId: string, settingId: string, val: any) => {
    setConfigData(prev => ({
        ...prev,
        [tabId]: {
            ...prev[tabId],
            modules: prev[tabId].modules.map(m => {
                if (m.id !== moduleId) return m;
                return {
                    ...m,
                    children: m.children.map(c => c.id === settingId ? { ...c, value: val } : c)
                };
            })
        }
    }));
  };

  // --- Render Helpers ---

  const renderConfigItem = (module: FeatureModule, item: ConfigItem) => {
    switch (item.type) {
        case ConfigType.CHECKBOX:
            return (
                <div key={item.id} className="col-span-1">
                    <Checkbox 
                        label={item.label} 
                        checked={item.value as boolean} 
                        onChange={(v) => updateSetting(activeTab, module.id, item.id, v)} 
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
                        onChange={(v) => updateSetting(activeTab, module.id, item.id, v)} 
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
                        onChange={(e) => updateSetting(activeTab, module.id, item.id, e.target.value)}
                        className="bg-neutral-800/50 border border-white/5 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:border-indigo-500/50 transition-colors w-full"
                     />
                 </div>
             );
        case ConfigType.SELECT:
             // Reusing the ModeSelect component logic if options provided
             return (
                 <div key={item.id} className="col-span-2">
                     {/* Using the previously defined ModeSelect if fitting, or a simple select */}
                     <span className="text-xs text-neutral-400 font-medium block mb-1">{item.label}</span>
                     {/* Simple implementation for now */}
                     <div className="flex bg-neutral-900/50 p-1 rounded-lg border border-white/5 gap-1">
                         {item.options?.map(opt => (
                             <button 
                                key={opt}
                                onClick={() => updateSetting(activeTab, module.id, item.id, opt)}
                                className={`flex-1 py-1 text-[10px] rounded ${item.value === opt ? 'bg-indigo-500 text-white' : 'text-neutral-500 hover:text-white'}`}
                             >
                                 {opt}
                             </button>
                         ))}
                     </div>
                 </div>
             );
        default:
            return null;
    }
  };

  const renderModule = (module: FeatureModule) => {
      // Determine if there are settings to render
      const hasSettings = module.children && module.children.length > 0;
      
      return (
          <FeatureCard
            key={module.id}
            title={module.title}
            description={module.description || ''}
            icon={module.icon || Box}
            enabled={module.enabled}
            onToggle={(v) => toggleModule(activeTab, module.id, v)}
          >
              {hasSettings ? (
                  <div className="grid grid-cols-2 gap-x-4 gap-y-2">
                      {module.children.map(child => renderConfigItem(module, child))}
                  </div>
              ) : null}
          </FeatureCard>
      );
  };

  // --- Main Render Logic ---

  if (activeTab === TabId.MUSIC) {
      return (
        <motion.div 
            key="music"
            initial={{ opacity: 0 }} 
            animate={{ opacity: 1 }} 
            exit={{ opacity: 0 }}
            className="h-full w-full"
        >
            <MusicPlayer setImmersiveMode={setImmersiveMode} />
        </motion.div>
      );
  }

  // Handle generic category rendering
  const currentCategory = configData[activeTab];

  // Fallback for SETTINGS or other non-generic tabs if not in APP_DATA
  const isGeneric = !!currentCategory;

  const renderContent = () => {
      if (isGeneric) {
          return (
              <div className="flex flex-col gap-4">
                  {currentCategory.modules.map(renderModule)}
              </div>
          );
      }

      // Hardcoded fallback for Settings (Global)
      if (activeTab === TabId.SETTINGS) {
          return (
              <div className="flex flex-col gap-6">
                  <div className="bg-neutral-900/40 rounded-xl p-5 border border-white/5">
                      <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
                          <Shield size={16} className="text-indigo-400"/> 账号与安全
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
                          <Database size={16} className="text-indigo-400"/> 缓存管理
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
      {/* Header */}
      <div className="flex items-center justify-between px-8 pt-8 pb-4 shrink-0">
        <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-bold text-white tracking-tight flex items-center gap-3">
                {isGeneric ? currentCategory.title : (activeTab === TabId.SETTINGS ? '全局设置' : '')}
            </h1>
            <p className="text-[10px] text-neutral-500 font-bold tracking-widest uppercase opacity-70">FPSMaster Configuration</p>
        </div>
        
        <button className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 hover:bg-indigo-500/20 transition-all group">
           <Bot size={14} className="text-indigo-400" />
           <span className="text-xs font-semibold text-indigo-200">AI 助手</span>
        </button>
      </div>

      {/* Content Scroll Area */}
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