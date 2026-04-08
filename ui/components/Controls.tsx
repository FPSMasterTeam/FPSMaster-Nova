import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronRight, LucideIcon, Check } from 'lucide-react';

// --- Toggle Switch ---
export const Toggle: React.FC<{ checked: boolean; onChange: (v: boolean) => void }> = ({ checked, onChange }) => {
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        onChange(!checked);
      }}
      className={`relative w-10 h-5 rounded-full transition-colors duration-200 ease-in-out focus:outline-none ${
        checked ? 'bg-indigo-500' : 'bg-neutral-700/50 border border-white/10'
      }`}
    >
      <motion.div
        className="absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white shadow-sm"
        animate={{ x: checked ? 20 : 0 }}
        transition={{ type: "spring", stiffness: 600, damping: 35 }}
      />
    </button>
  );
};

// --- Checkbox ---
interface CheckboxProps {
  label: string;
  checked: boolean;
  onChange: (val: boolean) => void;
  colorClass?: string;
}

export const Checkbox: React.FC<CheckboxProps> = ({ label, checked, onChange, colorClass = 'bg-indigo-500' }) => {
  return (
    <div 
      className="flex items-center gap-2.5 cursor-pointer group select-none py-1"
      onClick={() => onChange(!checked)}
    >
      <div className={`relative w-4 h-4 rounded-[5px] flex items-center justify-center transition-all duration-200 border ${
        checked 
          ? `${colorClass} border-transparent` 
          : 'bg-neutral-800/50 border-white/10 group-hover:border-white/20'
      }`}>
        <AnimatePresence>
        {checked && (
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            exit={{ scale: 0 }}
            transition={{ duration: 0.1 }}
          >
             <Check size={10} className="text-white" strokeWidth={3} />
          </motion.div>
        )}
        </AnimatePresence>
      </div>
      <span className={`text-xs font-medium transition-colors ${checked ? 'text-white' : 'text-neutral-400 group-hover:text-neutral-300'}`}>
        {label}
      </span>
    </div>
  );
};

// --- Slider ---
interface SliderProps {
  label: string;
  value: number;
  min: number;
  max: number;
  onChange: (val: number) => void;
  step?: number;
  suffix?: string;
}

export const Slider: React.FC<SliderProps> = ({ label, value, min, max, onChange, step = 1, suffix = '' }) => {
  const percentage = ((value - min) / (max - min)) * 100;
  
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(Number(e.target.value));
  };

  return (
    <div className="flex flex-col gap-1.5 py-1 select-none group">
      <div className="flex justify-between items-center">
        <span className="text-xs text-neutral-400 font-medium group-hover:text-neutral-200 transition-colors">{label}</span>
        <span className="text-[10px] text-indigo-200 font-mono bg-indigo-500/10 border border-indigo-500/20 px-1.5 py-0.5 rounded min-w-[2rem] text-center">
            {value}{suffix}
        </span>
      </div>
      <div className="relative w-full h-1 rounded-full bg-neutral-800/50 overflow-visible flex items-center">
        <div 
          className="absolute top-0 left-0 h-full rounded-full bg-indigo-500 shadow-[0_0_8px_rgba(99,102,241,0.3)]"
          style={{ width: `${percentage}%` }}
        />
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={handleChange}
          className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10"
        />
        {/* Handle */}
        <div 
            className="absolute w-3 h-3 bg-white rounded-full shadow-lg pointer-events-none transition-transform duration-100 ease-out group-hover:scale-110"
            style={{ left: `${percentage}%`, transform: `translateX(-50%)` }}
        />
      </div>
    </div>
  );
};

// --- Mode Selection ---
interface ModeSelectProps {
  label: string;
  options: string[];
  selected: string;
  onChange: (val: string) => void;
}

export const ModeSelect: React.FC<ModeSelectProps> = ({ label, options, selected, onChange }) => {
  return (
    <div className="flex flex-col gap-2 py-1">
      <span className="text-xs text-neutral-400 font-medium">{label}</span>
      <div className="flex bg-neutral-900/50 p-0.5 rounded-lg border border-white/5">
        {options.map((opt) => (
          <button
            key={opt}
            onClick={() => onChange(opt)}
            className={`flex-1 relative py-1 text-[10px] font-bold uppercase tracking-wide rounded-[6px] transition-colors ${
              selected === opt ? 'text-white' : 'text-neutral-500 hover:text-neutral-300'
            }`}
          >
            {selected === opt && (
              <motion.div
                layoutId={`mode-select-${label}`}
                className="absolute inset-0 bg-neutral-700/80 rounded-[6px] shadow-sm border border-white/5"
                transition={{ type: 'spring', stiffness: 500, damping: 30 }}
              />
            )}
            <span className="relative z-10">{opt}</span>
          </button>
        ))}
      </div>
    </div>
  );
};

// --- Feature Card ---
interface FeatureCardProps {
    title: string;
    description: string;
    icon: LucideIcon;
    enabled: boolean;
    onToggle: (v: boolean) => void;
    children?: React.ReactNode;
}

export const FeatureCard: React.FC<FeatureCardProps> = ({ title, description, icon: Icon, enabled, onToggle, children }) => {
    const [isExpanded, setIsExpanded] = useState(false);

    // If no children provided (or only empty/null children), disable expansion
    const hasContent = React.Children.count(children) > 0 && !!children;

    return (
        <div 
            className={`border transition-all duration-300 rounded-xl overflow-hidden ${
                enabled 
                ? 'bg-neutral-900/60 border-indigo-500/20' 
                : 'bg-neutral-900/20 border-white/5 hover:border-white/10'
            }`}
        >
            {/* Header Row */}
            <div 
                className={`p-3.5 flex items-center gap-3 select-none ${hasContent ? 'cursor-pointer' : 'cursor-default'}`}
                onClick={() => hasContent && setIsExpanded(!isExpanded)}
            >
                 <div className={`p-2 rounded-lg transition-colors duration-300 ${
                     enabled 
                     ? 'bg-indigo-500/10 text-indigo-400' 
                     : 'bg-white/5 text-neutral-500'
                 }`}>
                     <Icon size={18} />
                 </div>
                 
                 <div className="flex-1 min-w-0 flex flex-col">
                     <h3 className={`font-semibold text-sm transition-colors ${enabled ? 'text-white' : 'text-neutral-300'}`}>{title}</h3>
                     <p className="text-[10px] text-neutral-500 truncate">{description}</p>
                 </div>

                 <div className="flex items-center gap-3">
                     <Toggle checked={enabled} onChange={onToggle} />
                     {hasContent && (
                        <div className={`text-neutral-600 transition-transform duration-300 ${isExpanded ? 'rotate-90 text-neutral-400' : ''}`}>
                            <ChevronRight size={16} />
                        </div>
                     )}
                 </div>
            </div>

            {/* Config Body */}
            <AnimatePresence initial={false}>
                {isExpanded && hasContent && (
                    <motion.div
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: 'auto', opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={{ duration: 0.25, ease: 'easeInOut' }}
                    >
                        <div className="px-4 pb-4 pt-1 space-y-3 border-t border-white/5 mx-1">
                             <div className="h-0" /> 
                             {children}
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
};
