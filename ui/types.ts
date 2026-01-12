import { LucideIcon } from 'lucide-react';

export enum TabId {
  OPTIMIZE = 'optimize',
  RENDER = 'render',
  TOOLS = 'tools',
  INTERFACE = 'interface',
  MUSIC = 'music',
  SETTINGS = 'settings'
}

export interface SidebarItem {
  id: TabId;
  icon: LucideIcon;
  label: string;
}

export interface Song {
  id: string;
  title: string;
  artist: string;
  cover: string;
  duration: string;
  lyrics?: string[];
}

export interface Playlist {
  id: string;
  name: string;
  cover: string;
}

// --- Configuration System Types ---

export type ConfigValue = boolean | number | string;

export enum ConfigType {
  CHECKBOX = 'checkbox',
  SLIDER = 'slider',
  INPUT = 'input',
  SELECT = 'select'
}

export interface ConfigItem {
  id: string;
  label: string;
  type: ConfigType;
  value: ConfigValue;
  defaultValue?: ConfigValue;
  min?: number;      // For sliders
  max?: number;      // For sliders
  step?: number;     // For sliders
  suffix?: string;   // For sliders (e.g., "FPS", "px")
  options?: string[]; // For select
  placeholder?: string; // For input
}

export interface FeatureModule {
  id: string;
  title: string;
  description?: string; // Optional subtitle
  icon?: LucideIcon;    // Icon for the card
  enabled: boolean;     // Main toggle for the feature
  children: ConfigItem[]; // Sub-settings
}

export interface CategoryData {
  id: TabId;
  title: string;
  modules: FeatureModule[];
}