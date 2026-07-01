import React, { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Palette, ChevronDown } from 'lucide-react';
import { useT } from '../i18n';
import { MENU_BG_OPTIONS, CustomBackgroundUploader } from './MainMenuBackground';

interface MenuBackgroundPickerProps {
  current: string;
  onChange: (id: string) => void;
  disabled?: boolean;
}

// Top-right control on the main menu: click to expand the background-style list;
// picking "custom" reveals the image/video uploader inline.
export const MenuBackgroundPicker: React.FC<MenuBackgroundPickerProps> = ({ current, onChange, disabled }) => {
  const t = useT();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  // Close when clicking anywhere outside the control.
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    window.addEventListener('mousedown', onDown);
    return () => window.removeEventListener('mousedown', onDown);
  }, [open]);

  const pick = (id: string) => {
    onChange(id);
    // Keep the panel open for "custom" so the user can upload; close otherwise.
    if (id !== 'custom') setOpen(false);
  };

  return (
    <div ref={rootRef} className="absolute right-5 top-5 z-30">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        disabled={disabled}
        className="flex items-center gap-2 rounded-xl border border-white/10 bg-black/40 px-3 py-2 text-xs font-medium text-neutral-200 backdrop-blur-md transition-colors hover:bg-black/60 disabled:cursor-not-allowed disabled:opacity-40"
      >
        <Palette size={15} className="text-indigo-300" />
        <span>{t(`bg.${current}`)}</span>
        <ChevronDown size={14} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.15 }}
            className="absolute right-0 mt-2 w-64 rounded-xl border border-white/10 bg-[#0b0d12]/95 p-2 shadow-2xl backdrop-blur-xl"
          >
            <div className="grid grid-cols-2 gap-1.5">
              {MENU_BG_OPTIONS.map((id) => (
                <button
                  key={id}
                  type="button"
                  onClick={() => pick(id)}
                  className={`rounded-lg px-2.5 py-2 text-left text-xs font-medium transition-colors ${
                    current === id
                      ? 'bg-indigo-500/20 text-indigo-100 ring-1 ring-indigo-400/40'
                      : 'text-neutral-300 hover:bg-white/5'
                  }`}
                >
                  {t(`bg.${id}`)}
                </button>
              ))}
            </div>
            {current === 'custom' && (
              <div className="mt-2 border-t border-white/10 pt-2">
                <CustomBackgroundUploader />
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
