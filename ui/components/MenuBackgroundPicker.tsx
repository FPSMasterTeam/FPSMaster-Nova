import React, { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Palette } from 'lucide-react';
import { useT } from '../i18n';
import { MENU_BG_OPTIONS, CustomBackgroundUploader } from './MainMenuBackground';

interface MenuBackgroundPickerProps {
  current: string;
  onChange: (id: string) => void;
  disabled?: boolean;
}

// Top-right control on the main menu: a palette icon by default; hovering reveals the
// current-style label and the dropdown. Picking "custom" shows the uploader inline.
export const MenuBackgroundPicker: React.FC<MenuBackgroundPickerProps> = ({ current, onChange, disabled }) => {
  const t = useT();
  const [open, setOpen] = useState(false);
  const closeTimer = useRef<number | null>(null);

  useEffect(() => () => {
    if (closeTimer.current) clearTimeout(closeTimer.current);
  }, []);

  const cancelClose = () => {
    if (closeTimer.current) {
      clearTimeout(closeTimer.current);
      closeTimer.current = null;
    }
  };
  const scheduleClose = () => {
    cancelClose();
    closeTimer.current = window.setTimeout(() => setOpen(false), 220);
  };

  return (
    <div
      className="absolute right-5 top-5 z-30"
      onMouseEnter={() => {
        if (disabled) return;
        cancelClose();
        setOpen(true);
      }}
      onMouseLeave={scheduleClose}
    >
      <button
        type="button"
        disabled={disabled}
        className="flex items-center rounded-xl border border-white/10 bg-black/40 p-2 text-neutral-200 backdrop-blur-md transition-colors hover:bg-black/60 disabled:cursor-not-allowed disabled:opacity-40"
      >
        <Palette size={16} className="shrink-0 text-indigo-300" />
        <AnimatePresence initial={false}>
          {open && (
            <motion.span
              key="label"
              initial={{ opacity: 0, width: 0, marginLeft: 0 }}
              animate={{ opacity: 1, width: 'auto', marginLeft: 8 }}
              exit={{ opacity: 0, width: 0, marginLeft: 0 }}
              transition={{ duration: 0.16, ease: 'easeOut' }}
              className="overflow-hidden whitespace-nowrap pr-1 text-xs font-medium"
            >
              {t(`bg.${current}`)}
            </motion.span>
          )}
        </AnimatePresence>
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
                  onClick={() => onChange(id)}
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
