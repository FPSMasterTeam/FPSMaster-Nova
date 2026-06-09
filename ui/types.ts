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
  trackCount?: number;
}

// --- Netease Music Types ---

export interface NeteaseUserProfile {
  userId: number;
  nickname: string;
  avatarUrl: string;
  backgroundUrl: string;
  signature?: string;
}

export interface NeteaseUserDetail {
  level: number;
  listenSongs: number;
  profile: NeteaseUserProfile;
}

export interface NeteaseQrKeyResponse {
  data: {
    code: number;
    unikey: string;
  };
  code: number;
}

export interface NeteaseQrCreateResponse {
  data: {
    qrurl: string;
    qrimg: string; // base64
  };
  code: number;
}

export interface NeteaseQrCheckResponse {
  code: number; // 800, 801, 802, 803
  message: string;
  cookie: string;
}

export interface NeteaseDailyRecommendResponse {
  code: number;
  data: {
    dailySongs: {
      id: number;
      name: string;
      ar: { id: number; name: string }[];
      al: { id: number; name: string; picUrl: string };
      dt: number; // duration in ms
    }[];
  };
}

export interface NeteasePlaylist {
  id: number;
  type: number;
  name: string;
  copywriter: string;
  picUrl: string;
  playcount: number;
  trackCount: number;
  creator: NeteaseUserProfile;
}

export interface NeteaseRecommendResourceResponse {
  code: number;
  featureFirst: boolean;
  haveRcmdSongs: boolean;
  recommend: NeteasePlaylist[];
}

export interface NeteaseUserPlaylist {
  id: number;
  name: string;
  coverImgUrl: string;
  playCount: number;
  trackCount: number;
  userId: number;
  creator: NeteaseUserProfile;
  subscribed: boolean;
  description?: string;
}

export interface NeteaseUserPlaylistResponse {
  code: number;
  more: boolean;
  playlist: NeteaseUserPlaylist[];
}

export interface NeteaseTrack {
  id: number;
  name: string;
  ar: { id: number; name: string }[];
  al: { id: number; name: string; picUrl: string };
  dt: number; // duration in ms
}

export interface NeteasePlaylistTrackResponse {
  code: number;
  songs: NeteaseTrack[];
  privileges: any[];
}

export interface NeteaseSongUrlResponse {
  code: number;
  data: {
    id: number;
    url: string;
    br: number;
    size: number;
    md5: string;
    code: number;
    expi: number;
    type: string;
    gain: number;
    fee: number;
    level: string;
  }[];
}

export interface NeteaseLyricResponse {
  sgc: boolean;
  sfy: boolean;
  qfy: boolean;
  lrc?: {
    version: number;
    lyric: string;
  };
  tlyric?: {
    version: number;
    lyric: string;
  };
  yrc?: {
    version: number;
    lyric: string;
  };
  code: number;
}

// --- Configuration System Types ---

export type ConfigValue = boolean | number | string;

export enum ConfigType {
  CHECKBOX = 'checkbox',
  SLIDER = 'slider',
  INPUT = 'input',
  SELECT = 'select',
  KEYBIND = 'keybind',
  COLOR = 'color'
}

export interface ConfigOption {
  value: number | string;
  label: string;
}

export interface ColorChannels {
  red: string;
  green: string;
  blue: string;
  alpha?: string;
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
  options?: ConfigOption[]; // For select
  placeholder?: string; // For input
  channels?: ColorChannels; // For color
  alpha?: number; // For color
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
