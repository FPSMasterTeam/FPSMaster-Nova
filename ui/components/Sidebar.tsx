import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Zap, Monitor, Code, Palette, Music, Settings, ChevronRight, ChevronLeft } from 'lucide-react';
import { TabId, SidebarItem } from '../types';
import { useT } from '../i18n';

interface SidebarProps {
  activeTab: TabId;
  setActiveTab: (id: TabId) => void;
}

// `label` holds an i18n key, resolved with t() at render time.
const items: SidebarItem[] = [
  { id: TabId.OPTIMIZE, icon: Zap, label: 'nav.optimize' },
  { id: TabId.RENDER, icon: Monitor, label: 'nav.render' },
  { id: TabId.TOOLS, icon: Code, label: 'nav.tools' },
  { id: TabId.INTERFACE, icon: Palette, label: 'nav.interface' },
  { id: TabId.MUSIC, icon: Music, label: 'nav.music' },
  { id: TabId.SETTINGS, icon: Settings, label: 'nav.settings' },
];

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, setActiveTab }) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const t = useT();

  return (
    <motion.div 
        animate={{ width: isExpanded ? 240 : 70 }}
        transition={{ type: 'spring', stiffness: 300, damping: 30 }}
        className="h-full flex flex-col justify-between py-6 bg-black/20 border-r border-white/5 relative z-50"
    >
      
      {/* Top Section: Navigation */}
      <div className="flex flex-col gap-2 w-full px-3">
        {/* Spacer at top for aesthetics since logo is gone */}
        <div className="h-2"></div>

        {items.map((item) => (
           <SidebarButton 
             key={item.id}
             item={item} 
             isActive={activeTab === item.id} 
             isExpanded={isExpanded}
             onClick={() => setActiveTab(item.id)} 
           />
        ))}
      </div>

      {/* Bottom Section: Toggle */}
      <div className="w-full flex flex-col gap-4 px-3">
         {/* Divider */}
         <div className="h-px bg-white/5 w-full" />

         {/* Expand Toggle */}
         <button 
            onClick={() => setIsExpanded(!isExpanded)}
            className={`flex items-center h-10 rounded-xl hover:bg-white/5 transition-colors text-neutral-500 hover:text-white ${isExpanded ? 'px-0' : 'justify-center'}`}
         >
             {/* Icon Container to match button icon position */}
             <div className="min-w-[46px] w-[46px] flex items-center justify-center shrink-0">
                {isExpanded ? <ChevronLeft size={20} /> : <ChevronRight size={20} />}
             </div>
             {/* Text */}
             {isExpanded && (
                 <span className="text-xs font-medium whitespace-nowrap overflow-hidden">{t('nav.collapse')}</span>
             )}
         </button>
      </div>
    </motion.div>
  );
};

// Helper Component for Sidebar Button
const SidebarButton: React.FC<{ 
    item: SidebarItem; 
    isActive: boolean; 
    isExpanded: boolean;
    onClick: () => void;
}> = ({ item, isActive, isExpanded, onClick }) => {
  const t = useT();
  return (
    <button
      onClick={onClick}
      className={`relative h-10 flex items-center rounded-xl transition-all duration-200 group outline-none overflow-hidden ${
          isActive 
            ? 'text-white bg-white/10' 
            : 'text-neutral-500 hover:text-white hover:bg-white/5'
      }`}
    >
         {/* 
            Icon Container:
            - Fixed width of 46px (70px sidebar - 24px padding)
            - Centers the icon strictly
            - This prevents the icon from jumping when the parent button width expands
         */}
         <div className="min-w-[46px] w-[46px] flex items-center justify-center shrink-0 relative z-10">
            <item.icon 
                size={20} 
                className={`transition-colors ${isActive ? 'text-indigo-400' : 'group-hover:text-white'}`}
                strokeWidth={isActive ? 2.5 : 2}
            />
         </div>

         {/* Label */}
         <motion.span
            initial={false}
            animate={{ opacity: isExpanded ? 1 : 0 }}
            className="text-sm font-medium whitespace-nowrap relative z-10"
         >
             {t(item.label)}
         </motion.span>
    </button>
  );
}