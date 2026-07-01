import React, { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronRight, ChevronDown, LucideIcon, Check } from 'lucide-react';
import { useT, type TranslateFn } from '../i18n';

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
  const percentage = max > min ? ((value - min) / (max - min)) * 100 : 0;
  // Strip floating-point noise (e.g. 2.9000000000004 -> 2.9) for display.
  const display = Number.isInteger(value) ? value : Math.round(value * 1000) / 1000;

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(Number(e.target.value));
  };

  // Compact single-row layout: label ─ fixed-width track ─ value. No longer spans the whole card.
  return (
    <div className="flex items-center gap-3 py-1 select-none group">
      <span className="min-w-0 flex-1 truncate text-xs font-medium text-neutral-400 transition-colors group-hover:text-neutral-200">{label}</span>
      <div className="relative flex h-1 w-[150px] shrink-0 items-center rounded-full bg-neutral-800/60">
        <div
          className="absolute left-0 top-0 h-full rounded-full bg-indigo-500 shadow-[0_0_8px_rgba(99,102,241,0.3)]"
          style={{ width: `${percentage}%` }}
        />
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={handleChange}
          className="absolute inset-0 z-10 h-full w-full cursor-pointer opacity-0"
        />
        <div
          className="pointer-events-none absolute h-3 w-3 rounded-full bg-white shadow-lg transition-transform duration-100 ease-out group-hover:scale-110"
          style={{ left: `${percentage}%`, transform: 'translateX(-50%)' }}
        />
      </div>
      <span className="w-14 shrink-0 truncate text-right font-mono text-[10px] text-indigo-200">
        {display}{suffix}
      </span>
    </div>
  );
};

interface SelectOption {
  value: number | string;
  label: string;
}

interface SelectProps {
  label: string;
  value: number | string;
  options: SelectOption[];
  onChange: (value: number | string) => void;
}

interface CustomSelectProps {
  value: number | string;
  options: SelectOption[];
  onChange: (value: number | string) => void;
  disabled?: boolean;
  placeholder?: string;
  className?: string;
}

/**
 * Fully custom, in-DOM dropdown. Deliberately avoids the native <select> element:
 * under CEF off-screen rendering a native <select> popup is painted as a separate
 * popup frame, which has crashed the client's renderer (out-of-bounds buffer copy).
 * The option list here is rendered as absolutely-positioned DOM inside the main view,
 * so no CEF popup frame is ever produced.
 */
export const CustomSelect: React.FC<CustomSelectProps> = ({
  value,
  options,
  onChange,
  disabled = false,
  placeholder,
  className = '',
}) => {
  const t = useT();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handlePointer = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handlePointer);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handlePointer);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open]);

  const selected = options.find((option) => String(option.value) === String(value));

  return (
    <div ref={containerRef} className={`relative select-none ${className}`}>
      <button
        type="button"
        disabled={disabled}
        onClick={(event) => {
          event.stopPropagation();
          if (!disabled) setOpen((prev) => !prev);
        }}
        className={`flex w-full items-center justify-between gap-2 rounded-lg border px-3 py-1.5 text-xs text-white transition-colors focus:outline-none disabled:cursor-not-allowed disabled:opacity-50 ${
          open ? 'border-indigo-500/50 bg-neutral-800/70' : 'border-white/5 bg-neutral-800/50'
        }`}
      >
        <span className={`truncate ${selected ? 'text-white' : 'text-neutral-500'}`}>
          {selected ? selected.label : (placeholder ?? t('common.select'))}
        </span>
        <ChevronDown
          size={14}
          className={`shrink-0 text-neutral-400 transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
        />
      </button>
      <AnimatePresence initial={false}>
        {open && (
          // Rendered IN-FLOW (not absolute) so it expands the parent card instead of being clipped
          // by the card's overflow-hidden. Long lists still scroll within max-h.
          <motion.ul
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.15 }}
            className="mt-1 max-h-48 w-full overflow-y-auto overflow-x-hidden rounded-lg border border-white/10 bg-neutral-900/95 p-1 shadow-xl backdrop-blur-sm"
          >
            {options.map((option) => {
              const isSelected = String(option.value) === String(value);
              return (
                <li key={String(option.value)}>
                  <button
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation();
                      onChange(option.value);
                      setOpen(false);
                    }}
                    className={`flex w-full items-center justify-between gap-2 rounded-md px-2.5 py-1.5 text-left text-xs transition-colors ${
                      isSelected ? 'bg-indigo-500/20 text-white' : 'text-neutral-300 hover:bg-white/5'
                    }`}
                  >
                    <span className="truncate">{option.label}</span>
                    {isSelected && <Check size={12} className="shrink-0 text-indigo-300" strokeWidth={3} />}
                  </button>
                </li>
              );
            })}
          </motion.ul>
        )}
      </AnimatePresence>
    </div>
  );
};

export const SelectBox: React.FC<SelectProps> = ({ label, value, options, onChange }) => {
  return (
    <div className="flex items-center justify-between gap-3 py-1 select-none">
      <span className="min-w-0 flex-1 truncate text-xs font-medium text-neutral-400">{label}</span>
      <CustomSelect value={value} options={options} onChange={onChange} className="w-[160px] shrink-0" />
    </div>
  );
};

const GLFW_KEY_NAMES: Record<number, string> = {
  32: 'Space',
  256: 'Esc',
  257: 'Enter',
  258: 'Tab',
  259: 'Backspace',
  260: 'Insert',
  261: 'Delete',
  262: 'Right',
  263: 'Left',
  264: 'Down',
  265: 'Up',
  280: 'Caps Lock',
  290: 'F1',
  291: 'F2',
  292: 'F3',
  293: 'F4',
  294: 'F5',
  295: 'F6',
  296: 'F7',
  297: 'F8',
  298: 'F9',
  299: 'F10',
  300: 'F11',
  301: 'F12',
  340: 'Left Shift',
  341: 'Left Ctrl',
  342: 'Left Alt',
  344: 'Right Shift',
  345: 'Right Ctrl',
  346: 'Right Alt',
};

const keyCodeToGlfw = (event: React.KeyboardEvent<HTMLButtonElement>): number | null => {
  if (/^Key[A-Z]$/.test(event.code)) {
    return event.code.charCodeAt(3);
  }
  if (/^Digit[0-9]$/.test(event.code)) {
    return event.code.charCodeAt(5);
  }
  if (/^F([1-9]|1[0-2])$/.test(event.code)) {
    return 289 + Number(event.code.substring(1));
  }

  switch (event.code) {
    case 'Escape':
      return 256;
    case 'Enter':
      return 257;
    case 'Tab':
      return 258;
    case 'Backspace':
      return 259;
    case 'Insert':
      return 260;
    case 'Delete':
      return 261;
    case 'ArrowRight':
      return 262;
    case 'ArrowLeft':
      return 263;
    case 'ArrowDown':
      return 264;
    case 'ArrowUp':
      return 265;
    case 'CapsLock':
      return 280;
    case 'Space':
      return 32;
    case 'ShiftLeft':
      return 340;
    case 'ControlLeft':
      return 341;
    case 'AltLeft':
      return 342;
    case 'ShiftRight':
      return 344;
    case 'ControlRight':
      return 345;
    case 'AltRight':
      return 346;
    default:
      return null;
  }
};

const keyName = (value: number, t: TranslateFn): string => {
  if (value === 0) {
    return t('common.none');
  }
  if (GLFW_KEY_NAMES[value]) {
    return GLFW_KEY_NAMES[value];
  }
  if (value >= 65 && value <= 90) {
    return String.fromCharCode(value);
  }
  if (value >= 48 && value <= 57) {
    return String.fromCharCode(value);
  }
  return `Key ${value}`;
};

export const KeybindInput: React.FC<{ label: string; value: number; onChange: (value: number) => void }> = ({
  label,
  value,
  onChange,
}) => {
  const t = useT();
  return (
    <div className="flex flex-col gap-1.5 py-1">
      <span className="text-xs text-neutral-400 font-medium">{label}</span>
      <div className="flex gap-2">
        <button
          type="button"
          onKeyDown={(event) => {
            event.preventDefault();
            const nextValue = keyCodeToGlfw(event);
            if (nextValue !== null) {
              onChange(nextValue);
            }
          }}
          className="flex-1 rounded-lg border border-white/5 bg-neutral-800/50 px-3 py-1.5 text-left text-xs font-mono text-white transition-colors focus:outline-none focus:border-indigo-500/50"
        >
          {keyName(value, t)}
        </button>
        <button
          type="button"
          onClick={() => onChange(0)}
          className="rounded-lg border border-white/5 bg-white/5 px-2.5 py-1.5 text-xs text-neutral-300 transition-colors hover:bg-white/10"
        >
          {t('common.clear')}
        </button>
      </div>
    </div>
  );
};

interface ColorPickerProps {
  label: string;
  value: string;
  alpha?: number;
  onChange: (hex: string, alpha?: number) => void;
}

// --- Color math (HSV <-> hex), kept local so the picker has no native dependency ---
const clampNum = (n: number, min: number, max: number) => Math.min(max, Math.max(min, n));

const normalizeHex = (hex: string): string => {
  let h = (hex || '').trim().replace(/^#/, '');
  if (h.length === 3) h = h.split('').map((c) => c + c).join('');
  if (!/^[0-9a-fA-F]{6}$/.test(h)) return '#000000';
  return `#${h.toLowerCase()}`;
};

const hexToRgb = (hex: string) => {
  const h = normalizeHex(hex).slice(1);
  return {
    r: parseInt(h.slice(0, 2), 16),
    g: parseInt(h.slice(2, 4), 16),
    b: parseInt(h.slice(4, 6), 16),
  };
};

const rgbToHex = (r: number, g: number, b: number) => {
  const toHex = (n: number) => clampNum(Math.round(n), 0, 255).toString(16).padStart(2, '0');
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
};

const rgbToHsv = (r: number, g: number, b: number) => {
  const rn = r / 255, gn = g / 255, bn = b / 255;
  const max = Math.max(rn, gn, bn), min = Math.min(rn, gn, bn);
  const d = max - min;
  let h = 0;
  if (d !== 0) {
    if (max === rn) h = ((gn - bn) / d) % 6;
    else if (max === gn) h = (bn - rn) / d + 2;
    else h = (rn - gn) / d + 4;
    h *= 60;
    if (h < 0) h += 360;
  }
  return { h, s: max === 0 ? 0 : d / max, v: max };
};

const hsvToHex = (h: number, s: number, v: number) => {
  const c = v * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = v - c;
  let r = 0, g = 0, b = 0;
  if (h < 60) [r, g, b] = [c, x, 0];
  else if (h < 120) [r, g, b] = [x, c, 0];
  else if (h < 180) [r, g, b] = [0, c, x];
  else if (h < 240) [r, g, b] = [0, x, c];
  else if (h < 300) [r, g, b] = [x, 0, c];
  else [r, g, b] = [c, 0, x];
  return rgbToHex((r + m) * 255, (g + m) * 255, (b + m) * 255);
};

/**
 * Fully custom, in-DOM color picker. Deliberately avoids <input type="color">: under CEF
 * off-screen rendering that opens a native OS color panel / popup frame, the same class of
 * native popup that crashed the renderer. Everything here is plain DOM rendered in the main view.
 */
export const ColorPicker: React.FC<ColorPickerProps> = ({ label, value, alpha, onChange }) => {
  const [open, setOpen] = useState(false);
  const [hexDraft, setHexDraft] = useState(value);
  const [hsv, setHsv] = useState(() => {
    const { r, g, b } = hexToRgb(value);
    return rgbToHsv(r, g, b);
  });
  const containerRef = useRef<HTMLDivElement>(null);
  const svRef = useRef<HTMLDivElement>(null);
  const hueRef = useRef<HTMLDivElement>(null);
  const hsvRef = useRef(hsv);
  hsvRef.current = hsv;

  // Re-sync internal HSV from the prop when it changes from the outside (and not as a result
  // of our own edit). Comparing on hex avoids fighting with hue/sat the prop can't represent.
  useEffect(() => {
    setHexDraft(value);
    if (hsvToHex(hsvRef.current.h, hsvRef.current.s, hsvRef.current.v) !== normalizeHex(value)) {
      const { r, g, b } = hexToRgb(value);
      setHsv(rgbToHsv(r, g, b));
    }
  }, [value]);

  useEffect(() => {
    if (!open) return;
    const handlePointer = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handlePointer);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handlePointer);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open]);

  const applyHsv = (next: { h: number; s: number; v: number }) => {
    setHsv(next);
    hsvRef.current = next;
    onChange(hsvToHex(next.h, next.s, next.v), alpha);
  };

  const startDrag = (
    ref: React.RefObject<HTMLDivElement>,
    compute: (rect: DOMRect, clientX: number, clientY: number) => { h: number; s: number; v: number }
  ) => (event: React.MouseEvent) => {
    event.preventDefault();
    const move = (clientX: number, clientY: number) => {
      const rect = ref.current?.getBoundingClientRect();
      if (rect) applyHsv(compute(rect, clientX, clientY));
    };
    move(event.clientX, event.clientY);
    const onMove = (e: MouseEvent) => move(e.clientX, e.clientY);
    const onUp = () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  };

  const startSvDrag = startDrag(svRef, (rect, x, y) => ({
    ...hsvRef.current,
    s: clampNum((x - rect.left) / rect.width, 0, 1),
    v: clampNum(1 - (y - rect.top) / rect.height, 0, 1),
  }));

  const startHueDrag = startDrag(hueRef, (rect, x) => ({
    ...hsvRef.current,
    h: clampNum((x - rect.left) / rect.width, 0, 1) * 360,
  }));

  // Alpha (0-255) drag on a custom gradient strip — no native <input type=range>.
  const alphaRef = useRef<HTMLDivElement>(null);
  const startAlphaDrag = (event: React.MouseEvent) => {
    event.preventDefault();
    const move = (clientX: number) => {
      const rect = alphaRef.current?.getBoundingClientRect();
      if (!rect) return;
      const frac = clampNum((clientX - rect.left) / rect.width, 0, 1);
      onChange(value, Math.round(frac * 255));
    };
    move(event.clientX);
    const onMove = (e: MouseEvent) => move(e.clientX);
    const onUp = () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  };

  const hasAlpha = alpha !== undefined;
  const alphaFrac = hasAlpha ? clampNum(alpha! / 255, 0, 1) : 1;
  // Checkerboard so transparency reads correctly on the swatch.
  const checker =
    'repeating-conic-gradient(#3f3f46 0% 25%, #52525b 0% 50%) 50% / 8px 8px';

  return (
    <div ref={containerRef} className="relative py-1 select-none">
      <div className="flex items-center justify-between gap-3">
        <span className="min-w-0 flex-1 truncate text-xs font-medium text-neutral-400">{label}</span>
        <div className="flex shrink-0 items-center gap-2">
          <span className="font-mono text-[10px] text-neutral-500">
            {value.toUpperCase()}{hasAlpha ? ` · ${Math.round(alpha!)}` : ''}
          </span>
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              setOpen((prev) => !prev);
            }}
            className="relative h-6 w-9 shrink-0 cursor-pointer overflow-hidden rounded-md border border-white/15 transition-colors hover:border-white/30"
            style={{ background: checker }}
            aria-label={label}
          >
            <span className="absolute inset-0" style={{ backgroundColor: value, opacity: alphaFrac }} />
          </button>
        </div>
      </div>
      <AnimatePresence initial={false}>
        {open && (
          // In-flow popover (expands the parent, never clipped). SV plane + hue + alpha, all in-DOM.
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.15 }}
            className="mt-2 w-full overflow-hidden rounded-xl border border-white/10 bg-neutral-900/95 p-3 shadow-2xl backdrop-blur-md"
          >
            <div
              ref={svRef}
              onMouseDown={startSvDrag}
              className="relative h-32 w-full cursor-crosshair overflow-hidden rounded-lg"
              style={{ backgroundColor: `hsl(${hsv.h}, 100%, 50%)` }}
            >
              <div className="absolute inset-0" style={{ background: 'linear-gradient(to right, #fff, rgba(255,255,255,0))' }} />
              <div className="absolute inset-0" style={{ background: 'linear-gradient(to top, #000, rgba(0,0,0,0))' }} />
              <div
                className="pointer-events-none absolute h-3.5 w-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-[0_0_3px_rgba(0,0,0,0.7)]"
                style={{ left: `${hsv.s * 100}%`, top: `${(1 - hsv.v) * 100}%` }}
              />
            </div>
            <div
              ref={hueRef}
              onMouseDown={startHueDrag}
              className="relative mt-3 h-2.5 w-full cursor-pointer rounded-full"
              style={{ background: 'linear-gradient(to right, #f00 0%, #ff0 17%, #0f0 33%, #0ff 50%, #00f 67%, #f0f 83%, #f00 100%)' }}
            >
              <div
                className="pointer-events-none absolute top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-[0_0_3px_rgba(0,0,0,0.7)]"
                style={{ left: `${(hsv.h / 360) * 100}%` }}
              />
            </div>
            {hasAlpha && (
              <div
                ref={alphaRef}
                onMouseDown={startAlphaDrag}
                className="relative mt-3 h-2.5 w-full cursor-pointer overflow-hidden rounded-full"
                style={{ background: checker }}
              >
                <div
                  className="absolute inset-0 rounded-full"
                  style={{ background: `linear-gradient(to right, rgba(0,0,0,0), ${value})` }}
                />
                <div
                  className="pointer-events-none absolute top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-[0_0_3px_rgba(0,0,0,0.7)]"
                  style={{ left: `${alphaFrac * 100}%` }}
                />
              </div>
            )}
            <input
              type="text"
              spellCheck={false}
              value={hexDraft}
              onChange={(event) => {
                const raw = event.target.value;
                setHexDraft(raw);
                if (/^#?[0-9a-fA-F]{6}$/.test(raw)) {
                  const { r, g, b } = hexToRgb(normalizeHex(raw));
                  applyHsv(rgbToHsv(r, g, b));
                }
              }}
              onBlur={() => setHexDraft(value)}
              className="mt-3 w-full rounded-md border border-white/5 bg-neutral-800/50 px-2 py-1 text-center font-mono text-xs text-white focus:border-indigo-500/50 focus:outline-none"
            />
          </motion.div>
        )}
      </AnimatePresence>
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

// --- Collapsible settings group (分层展开) ---
export const CollapsibleGroup: React.FC<{
  title: string;
  defaultCollapsed?: boolean;
  children: React.ReactNode;
}> = ({ title, defaultCollapsed = false, children }) => {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  return (
    <div className="overflow-hidden rounded-lg border border-white/5 bg-black/20">
      <button
        type="button"
        onClick={() => setCollapsed((c) => !c)}
        className="flex w-full items-center justify-between px-3 py-2 select-none transition-colors hover:bg-white/[0.03]"
      >
        <span className="text-[11px] font-semibold uppercase tracking-wide text-neutral-300">{title}</span>
        <ChevronRight
          size={13}
          className={`text-neutral-500 transition-transform duration-200 ${collapsed ? '' : 'rotate-90'}`}
        />
      </button>
      <AnimatePresence initial={false}>
        {!collapsed && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: 'easeInOut' }}
            className="overflow-hidden"
          >
            <div className="space-y-1.5 border-t border-white/5 px-3 pb-2.5 pt-1.5">{children}</div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

// --- Feature Card ---
interface FeatureCardProps {
    title: string;
    description: string;
    icon: LucideIcon;
    enabled: boolean;
    canBeEnabled: boolean;
    onToggle: (v: boolean) => void;
    children?: React.ReactNode;
    defaultExpanded?: boolean;
}

export const FeatureCard: React.FC<FeatureCardProps> = ({ title, description, icon: Icon, enabled, canBeEnabled, onToggle, children, defaultExpanded = false }) => {
    const [isExpanded, setIsExpanded] = useState(defaultExpanded);

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

                {canBeEnabled && (<div className="flex items-center gap-3">
                    <Toggle checked={enabled} onChange={onToggle}/>
                    {hasContent && (
                        <div className={`text-neutral-600 transition-transform duration-300 ${isExpanded ? 'rotate-90 text-neutral-400' : ''}`}><ChevronRight size={16}/>
                    </div>
                    )}
                </div>)}
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
